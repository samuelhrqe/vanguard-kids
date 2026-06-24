/**
 * @file    main.c
 * @brief   VanGuard Kids — Firmware ESP32
 *
 * Fluxo:
 *   Potenciômetro (simulando sensor de peso)
 *     → adc_manager (leitura ADC)
 *       → vTaskSeats  (detecção de mudança de estado)
 *         → xSeatQueue (FreeRTOS Queue)
 *           → vTaskMQTT (publicação MQTT)
 */

#include <stdio.h>
#include <stdbool.h>
#include <string.h>
#include <inttypes.h>  /* PRId64 */

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_log.h"
#include "time_manager.h"
#include "mqtt.h"
#include "wifi.h"

#include "adc_manager.h"

// Memory task size
#define MEMORY_TASK (2048 * 2)

// ESP_LOG -> Needed to ESP_LOGI, ESP_LOGE, etc in Wokwi Web Simulator
// #define CONFIG_LOG_DEFAULT_LEVEL_VERBOSE 1
// #define CONFIG_LOG_MAXIMUM_LEVEL         5

// Seats
#define SEAT_COUNT            4
#define MAX_WEIGHT_GRAMS      50000.0f
#define OCCUPIED_THRESHOLD_G  5000.0f

// Queue length for seat messages
#define SEAT_QUEUE_LENGTH (SEAT_COUNT * 10)

// Heartbeat interval in milliseconds
#define HEARTBEAT_INTERVAL_MS 30000

// MQTT retry delay in milliseconds
#define MQTT_RETRY_DELAY_MS   500

// MQTT topics
#define MQTT_TOPIC_SEAT_FMT  "vanguard-kids/seats/%s"
#define MQTT_TOPIC_HEARTBEAT "vanguard-kids/heartbeat"

static const char* TAG = "MAIN";

typedef enum {
	SEAT_01 = 0,
	SEAT_02,
	SEAT_03,
	SEAT_04
} SeatId;

typedef struct {
	int64_t ts;
	float voltage;
	float weight_grams;
	SeatId id;
	int adc_raw;
	char name[12];
	bool occupied;
} SeatMessage;

typedef struct {
	const char* name;
	adc_channel_t adc_channel;
	int adc_raw;
	float voltage;
	float weight_grams;
	SeatId id;
	bool initialized;
	bool occupied;
	bool last_occupied;
} Seat;

ESP_STATIC_ASSERT(sizeof(SeatMessage) == 40, "SeatMessage size mismatch");
ESP_STATIC_ASSERT(sizeof(Seat) == 28, "Seat size mismatch");

// Seat configurations — mapping seat IDs to ADC channels and names
static Seat seats[SEAT_COUNT] = {
	{.id = SEAT_01, .name = "seat-01", .initialized = false, .adc_channel = ADC_CHANNEL_6 /* GPIO34 */ },
	{.id = SEAT_02, .name = "seat-02", .initialized = false, .adc_channel = ADC_CHANNEL_7 /* GPIO35 */ },
	{.id = SEAT_03, .name = "seat-03", .initialized = false, .adc_channel = ADC_CHANNEL_4 /* GPIO32 */ },
	{.id = SEAT_04, .name = "seat-04", .initialized = false, .adc_channel = ADC_CHANNEL_5 /* GPIO33 */ },
};

static TaskHandle_t  xSeatsTaskHandle;
static TaskHandle_t  xMQTTTaskHandle;
static QueueHandle_t xSeatQueue;

static bool update_seat(Seat* seat);
static void vTaskSeats(void* pvParameters);
static void vTaskMQTT(void* pvParameters);

void app_main(void) {
	esp_log_level_set(TAG, ESP_LOG_VERBOSE);

	ESP_ERROR_CHECK(nvs_flash_init());
	wifi_init_sta();

	// SNTP sync
	time_manager_init();

	vTaskDelay(pdMS_TO_TICKS(2000));

	mqtt_app_start();

	ChannelConfig adc_channels[SEAT_COUNT] = {
		{.channel = ADC_CHANNEL_6 },
		{.channel = ADC_CHANNEL_7 },
		{.channel = ADC_CHANNEL_4 },
		{.channel = ADC_CHANNEL_5 },
	};

	ESP_ERROR_CHECK(adc_manager_init(adc_channels, SEAT_COUNT));

	xSeatQueue = xQueueCreate(SEAT_QUEUE_LENGTH, sizeof(SeatMessage));
	if (xSeatQueue == NULL) {
		ESP_LOGE(TAG, "Falha ao criar fila de assentos");
		return;
	}

	xTaskCreate(vTaskMQTT, "mqtt_task", MEMORY_TASK, NULL, 6, &xMQTTTaskHandle);
	xTaskCreate(vTaskSeats, "seats_task", MEMORY_TASK, NULL, 4, &xSeatsTaskHandle);
}

static bool update_seat(Seat* seat) {
	int raw = 0;

	esp_err_t err = adc_manager_read_raw(seat->adc_channel, &raw);
	if (err != ESP_OK) {
		ESP_LOGE(TAG, "Erro ao ler %s: %s", seat->name, esp_err_to_name(err));
		return false;
	}

	seat->adc_raw = raw;
	seat->voltage = adc_manager_raw_to_voltage(raw);
	seat->weight_grams = adc_manager_raw_to_grams(raw, MAX_WEIGHT_GRAMS);
	seat->occupied = seat->weight_grams >= OCCUPIED_THRESHOLD_G;
	return true;
}

static void vTaskSeats(void* pvParameters) {
	UNUSED(pvParameters);

	SeatMessage message;

	while (1) {
		ESP_LOGI(TAG, "-----------------------------------------------------");

		for (int i = 0; i < SEAT_COUNT; i++) {
			Seat* seat = &seats[i];

			if (!update_seat(seat)) {
				continue;
			}

			ESP_LOGI(
				TAG,
				"%s | ADC=%d | V=%.2f | Peso=%.0f g | Ocupado=%s",
				seat->name,
				seat->adc_raw,
				seat->voltage,
				seat->weight_grams,
				seat->occupied ? "SIM" : "NAO"
			);

			bool should_publish = !seat->initialized || (seat->occupied != seat->last_occupied);

			if (!should_publish) {
				continue;
			}

			seat->last_occupied = seat->occupied;
			seat->initialized = true;

			// Prepare the message to send to the queue
			message.id = seat->id;
			message.adc_raw = seat->adc_raw;
			message.voltage = seat->voltage;
			message.weight_grams = seat->weight_grams;
			message.occupied = seat->occupied;
			message.ts = get_epoch();

			strncpy(message.name, seat->name, sizeof(message.name));
			message.name[sizeof(message.name) - 1] = '\0';

			if (xQueueSend(xSeatQueue, &message, pdMS_TO_TICKS(100)) != pdPASS) {
				ESP_LOGW(TAG, "Fila cheia — mensagem descartada: %s", message.name);
			}
			else {
				ESP_LOGI(TAG, "Mudança enfileirada: %s → %s", message.name, message.occupied ? "OCUPADO" : "LIVRE");
			}
		}

		vTaskDelay(pdMS_TO_TICKS(1000));
	}
}

static void vTaskMQTT(void* pvParameters) {
	UNUSED(pvParameters);

	bool first_run = true;
	SeatMessage msg;
	char topic[64];
	char payload[256];

	// Heartbeat timestamp tracking
	TickType_t last_heartbeat = xTaskGetTickCount();

	while (1) {
		TickType_t now = xTaskGetTickCount();

		// Check if it's time to send a heartbeat message
		bool heartbeat_due = (now - last_heartbeat) >= pdMS_TO_TICKS(HEARTBEAT_INTERVAL_MS);

		if (heartbeat_due || first_run) {
			if (mqtt_is_connected()) {
				int64_t ts = get_epoch();

				snprintf(payload, sizeof(payload),
					"{\"client_id\":\"%s\",\"ts\":%" PRId64 ",\"seats\":%d}",
					CONFIG_MQTT_CLIENT_ID,
					ts,
					SEAT_COUNT
				);

				mqtt_publish(MQTT_TOPIC_HEARTBEAT, payload);
				ESP_LOGI(TAG, "Heartbeat publicado");

				first_run = false;

			} else {
				ESP_LOGW(TAG, "Heartbeat pendente. MQTT offline");
			}

			last_heartbeat = now;
		}

		/*
		 * Wait for a seat message from the queue with a timeout to allow heartbeat checks.
		 * If no message is received within the timeout, the loop continues to check for heartbeat.
		 */
		if (xQueueReceive(xSeatQueue, &msg, pdMS_TO_TICKS(1000)) != pdPASS) {
			continue; // No message received, continue to next iteration to check heartbeat
		}

		// Prepare MQTT topic and payload for the seat message
		snprintf(topic, sizeof(topic), MQTT_TOPIC_SEAT_FMT, msg.name);

		snprintf(
			payload,
			sizeof(payload),
			"{"
			"\"client_id\":\"%s\","
			"\"seat\":\"%s\","
			"\"adc_raw\":%d,"
			"\"voltage\":%.2f,"
			"\"weight_g\":%.0f,"
			"\"occupied\":%s,"
			"\"ts\":%" PRId64
			"}",
			CONFIG_MQTT_CLIENT_ID,
			msg.name,
			msg.adc_raw,
			msg.voltage,
			msg.weight_grams,
			msg.occupied ? "true" : "false",
			msg.ts
		);

		if (mqtt_is_connected()) {
			mqtt_publish(topic, payload);
			ESP_LOGI(TAG, "Publicado → %s", topic);
		}
		else {
			// MQTT offline — re-enqueue the message for retry
			ESP_LOGW(TAG, "MQTT offline. Re-enfileirando: %s", msg.name);

			if (xQueueSendToFront(xSeatQueue, &msg, 0) != pdPASS) {
				ESP_LOGE(TAG, "Fila cheia. Mensagem perdida: %s", msg.name);
			}

			vTaskDelay(pdMS_TO_TICKS(MQTT_RETRY_DELAY_MS));
		}
	}
}