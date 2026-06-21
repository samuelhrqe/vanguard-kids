#include "adc_manager.h"

#include "esp_adc/adc_oneshot.h"
#include "esp_log.h"

#define ADC_UNIT_USED      ADC_UNIT_1
#define ADC_ATTEN_USED     ADC_ATTEN_DB_11
#define ADC_BITWIDTH_USED  ADC_BITWIDTH_12

static const char *TAG = "ADC Manager";

static adc_oneshot_unit_handle_t adc_handle;
static bool adc_initialized = false;

esp_err_t adc_manager_init(const ChannelConfig *channels, int channel_count) {
  if (channels == NULL || channel_count <= 0) {
    return ESP_ERR_INVALID_ARG;
  }

  if (channel_count > ADC_MANAGER_MAX_CHANNELS) {
    return ESP_ERR_INVALID_ARG;
  }

  adc_oneshot_unit_init_cfg_t init_config = {
    .unit_id = ADC_UNIT_USED,
  };

  esp_err_t err = adc_oneshot_new_unit(&init_config, &adc_handle);
  if (err != ESP_OK) {
    ESP_LOGE(TAG, "Erro ao inicializar ADC: %s", esp_err_to_name(err));
    return err;
  }

  adc_oneshot_chan_cfg_t channel_config = {
    .atten = ADC_ATTEN_USED,
    .bitwidth = ADC_BITWIDTH_USED,
  };

  for (int i = 0; i < channel_count; i++) {
    err = adc_oneshot_config_channel(
      adc_handle,
      channels[i].channel,
      &channel_config
    );

    if (err != ESP_OK) {
      ESP_LOGE(
        TAG,
        "Erro ao configurar canal ADC %d: %s",
        channels[i].channel,
        esp_err_to_name(err)
      );
      return err;
    }

    ESP_LOGI(TAG, "Canal ADC configurado: %d", channels[i].channel);
  }

  adc_initialized = true;

  return ESP_OK;
}

esp_err_t adc_manager_read_raw(adc_channel_t channel, int *raw_value)
{
  if (!adc_initialized) {
    return ESP_ERR_INVALID_STATE;
  }

  if (raw_value == NULL) {
    return ESP_ERR_INVALID_ARG;
  }

  return adc_oneshot_read(adc_handle, channel, raw_value);
}

float adc_manager_raw_to_voltage(int raw_value)
{
  return ((float) raw_value / 4095.0f) * 3.3f;
}

float adc_manager_raw_to_grams(int raw_value, float max_weight_grams)
{
  return ((float) raw_value / 4095.0f) * max_weight_grams;
}