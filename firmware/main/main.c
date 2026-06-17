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

// ESP_LOG -> Needed to ESP_LOGI, ESP_LOGE, etc in Wokwi
#define CONFIG_LOG_DEFAULT_LEVEL_VERBOSE 1
#define CONFIG_LOG_MAXIMUM_LEVEL         5

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
	SeatId id;
	char name[12];
	int adc_raw;
	float voltage;
	float weight_grams;
	bool occupied;
	int64_t ts;
} SeatMessage;

typedef struct {
	SeatId id;
	const char* name;
	adc_channel_t adc_channel;
	int adc_raw;
	float voltage;
	float weight_grams;
	bool occupied;
	bool last_occupied;
} Seat;

// Seat configurations — mapping seat IDs to ADC channels and names
static Seat seats[SEAT_COUNT] = {
	{.id = SEAT_01, .name = "seat-01", .adc_channel = ADC_CHANNEL_6 /* GPIO34 */ },
	{.id = SEAT_02, .name = "seat-02", .adc_channel = ADC_CHANNEL_7 /* GPIO35 */ },
	{.id = SEAT_03, .name = "seat-03", .adc_channel = ADC_CHANNEL_4 /* GPIO32 */ },
	{.id = SEAT_04, .name = "seat-04", .adc_channel = ADC_CHANNEL_5 /* GPIO33 */ },
};

static TaskHandle_t  xSeatsTaskHandle;
static TaskHandle_t  xMQTTTaskHandle;
static QueueHandle_t xSeatQueue;

static void update_seat(Seat* seat);
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

	xTaskCreate(vTaskSeats, "seats_task", MEMORY_TASK, NULL, 4, &xSeatsTaskHandle);
	xTaskCreate(vTaskMQTT, "mqtt_task", MEMORY_TASK, NULL, 6, &xMQTTTaskHandle);
}

static void update_seat(Seat* seat) {
	int raw = 0;

	esp_err_t err = adc_manager_read_raw(seat->adc_channel, &raw);
	if (err != ESP_OK) {
		ESP_LOGE(TAG, "Erro ao ler %s: %s", seat->name, esp_err_to_name(err));
		return;
	}

	seat->adc_raw = raw;
	seat->voltage = adc_manager_raw_to_voltage(raw);
	seat->weight_grams = adc_manager_raw_to_grams(raw, MAX_WEIGHT_GRAMS);
	seat->occupied = seat->weight_grams >= OCCUPIED_THRESHOLD_G;
}

static void vTaskSeats(void* pvParameters) {
	UNUSED(pvParameters);

	SeatMessage message;

	while (1) {
		ESP_LOGI(TAG, "-----------------------------------------------------");

		for (int i = 0; i < SEAT_COUNT; i++) {
			update_seat(&seats[i]);

			ESP_LOGI(
				TAG,
				"%s | ADC=%d | V=%.2f | Peso=%.0f g | Ocupado=%s",
				seats[i].name,
				seats[i].adc_raw,
				seats[i].voltage,
				seats[i].weight_grams,
				seats[i].occupied ? "SIM" : "NAO"
			);

			// If the state hasn't changed, skip sending to the queue
			bool state_changed = (seats[i].occupied != seats[i].last_occupied);

			if (!state_changed) {
				continue;
			}

			seats[i].last_occupied = seats[i].occupied;

			// Prepare the message to send to the queue
			message.id = seats[i].id;
			message.adc_raw = seats[i].adc_raw;
			message.voltage = seats[i].voltage;
			message.weight_grams = seats[i].weight_grams;
			message.occupied = seats[i].occupied;
			message.ts = get_epoch();

			strncpy(message.name, seats[i].name, sizeof(message.name));
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

	SeatMessage msg;
	char topic[64];
	char payload[256];

	// Heartbeat timestamp tracking
	TickType_t last_heartbeat = xTaskGetTickCount();

	while (1) {
		TickType_t now = xTaskGetTickCount();

		if ((now - last_heartbeat) >= pdMS_TO_TICKS(HEARTBEAT_INTERVAL_MS)) {
			if (mqtt_is_connected()) {
				int64_t ts = get_epoch();

				snprintf(payload, sizeof(payload),
					"{\"ts\":%" PRId64 ",\"seats\":%d}",
					ts,
					SEAT_COUNT
				);

				mqtt_publish(MQTT_TOPIC_HEARTBEAT, payload);
				ESP_LOGI(TAG, "Heartbeat publicado");
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
			ESP_LOGW(TAG, "MQTT offline — re-enfileirando: %s", msg.name);

			if (xQueueSendToFront(xSeatQueue, &msg, 0) != pdPASS) {
				ESP_LOGE(TAG, "Fila cheia — mensagem perdida: %s", msg.name);
			}

			vTaskDelay(pdMS_TO_TICKS(MQTT_RETRY_DELAY_MS));
		}
	}
}