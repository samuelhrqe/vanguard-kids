#include "time_manager.h"

static const char* TAG = "Time Manager";

static bool s_time_synced = false;

void time_manager_init(void) {
  ESP_LOGI(TAG, "Iniciando SNTP");

  esp_sntp_setoperatingmode(SNTP_OPMODE_POLL);
  esp_sntp_setservername(0, "pool.ntp.org");
  esp_sntp_setservername(1, "time.google.com");

  esp_sntp_init();

  int retry = 0;
  const int max_retries = 20;

  while (sntp_get_sync_status() != SNTP_SYNC_STATUS_COMPLETED && retry < max_retries) {
    vTaskDelay(pdMS_TO_TICKS(500));
    retry++;
  }

  if (retry < max_retries) {
    s_time_synced = true;
    ESP_LOGI(TAG, "Relógio sincronizado");

    char buf[32];
    get_timestamp(buf, sizeof(buf));
    ESP_LOGI(TAG, "Hora atual: %s (BRT) | Epoch Time: %lld", buf, (int64_t)get_epoch());
  } else {
    ESP_LOGW(TAG, "Falha ao sincronizar relógio");
  }
}

bool time_is_synced(void) {
  return s_time_synced;
}

uint64_t get_epoch(void) {
  time_t now;
  time(&now);

  return (uint64_t)now;
}

void get_timestamp(char *buffer, size_t buffer_size) {
  time_t now;
  struct tm timeinfo;

  setenv("TZ", "BRT3", 1);
  tzset();

  now = get_epoch();
  localtime_r(&now, &timeinfo);

  strftime(buffer, buffer_size, "%Y-%m-%dT%H:%M:%S", &timeinfo);
}