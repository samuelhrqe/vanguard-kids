#ifndef ADC_MANAGER_H
#define ADC_MANAGER_H

#include "esp_err.h"
#include "hal/adc_types.h"

#define ADC_MANAGER_MAX_CHANNELS 8

typedef struct {
  adc_channel_t channel;
} ChannelConfig;

esp_err_t adc_manager_init(const ChannelConfig *channel, int channel_count);

esp_err_t adc_manager_read_raw(adc_channel_t channel, int *raw_value);

float adc_manager_raw_to_voltage(int raw_value);

float adc_manager_raw_to_grams(int raw_value, float max_weight_grams);

#endif