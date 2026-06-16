#include <stdio.h>
#include <stdbool.h>
#include <string.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_log.h"

#include "adc_manager.h"

// #define TAG __func__

#define CONFIG_LOG_DEFAULT_LEVEL_VERBOSE 1
#define CONFIG_LOG_MAXIMUM_LEVEL  5

#define SEAT_COUNT 4

#define MAX_WEIGHT_GRAMS      50000.0f
#define OCCUPIED_THRESHOLD_G  5000.0f

static const char *TAG = "MAIN";

typedef enum {
  SEAT_01 = 0,
  SEAT_02,
  SEAT_03,
  SEAT_04
} SeatId;

typedef struct {
  SeatId id;
  const char *name;
  adc_channel_t adc_channel;
  int adc_raw;
  float voltage;
  float weight_grams;
  bool occupied;
} Seat;

static Seat seats[SEAT_COUNT] = {
  {
    .id = SEAT_01,
    .name = "seat-01",
    .adc_channel = ADC_CHANNEL_6, // GPIO34
  },
  {
    .id = SEAT_02,
    .name = "seat-02",
    .adc_channel = ADC_CHANNEL_7, // GPIO35
  },
  {
    .id = SEAT_03,
    .name = "seat-03",
    .adc_channel = ADC_CHANNEL_4, // GPIO32
  },
  {
    .id = SEAT_04,
    .name = "seat-04",
    .adc_channel = ADC_CHANNEL_5, // GPIO33
  }
};

static void update_seat(Seat *seat)
{
  int raw = 0;

  esp_err_t err = adc_manager_read_raw(seat->adc_channel, &raw);

  if (err != ESP_OK) {
    ESP_LOGE(
      TAG,
      "Erro ao ler %s: %s",
      seat->name,
      esp_err_to_name(err)
    );
    return;
  }

  seat->adc_raw = raw;
  seat->voltage = adc_manager_raw_to_voltage(raw);
  seat->weight_grams = adc_manager_raw_to_grams(raw, MAX_WEIGHT_GRAMS);
  seat->occupied = seat->weight_grams >= OCCUPIED_THRESHOLD_G;
}

static void seats_task(void *pvParameters)
{
  while (1) {
    ESP_LOGI(TAG, "-----------------------------");

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
    }

    vTaskDelay(pdMS_TO_TICKS(1000));
  }
}

void app_main(void) {
  esp_log_level_set(TAG, ESP_LOG_VERBOSE);
  ChannelConfig adc_channels[SEAT_COUNT] = {
    { .channel = ADC_CHANNEL_6 },
    { .channel = ADC_CHANNEL_7 },
    { .channel = ADC_CHANNEL_4 },
    { .channel = ADC_CHANNEL_5 }
  };

  ESP_ERROR_CHECK(adc_manager_init(adc_channels, SEAT_COUNT));

  xTaskCreate(
    seats_task,
    "seats_task",
    4096,
    NULL,
    5,
    NULL
  );
}