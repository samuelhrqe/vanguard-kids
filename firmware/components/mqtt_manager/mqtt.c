#include <stdio.h>
#include "mqtt.h"

// MQTT event group handle
static EventGroupHandle_t mqtt_event_group;

esp_mqtt_client_handle_t client;

static esp_err_t mqtt_event_handler_cb(esp_mqtt_event_handle_t event) {
  switch (event->event_id) {
    case MQTT_EVENT_CONNECTED:
      xEventGroupSetBits(mqtt_event_group, MQTT_CONNECTED_BIT);
      xEventGroupClearBits(mqtt_event_group, MQTT_DISCONNECTED_BIT);
      ESP_LOGI(TAG_MQTT, "Connected to MQTT broker");
      break;
    case MQTT_EVENT_DISCONNECTED:
      xEventGroupSetBits(mqtt_event_group, MQTT_DISCONNECTED_BIT);
      xEventGroupClearBits(mqtt_event_group, MQTT_CONNECTED_BIT);
      ESP_LOGW(TAG_MQTT, "Disconnected from MQTT broker");
      break;
    case MQTT_EVENT_SUBSCRIBED:
      ESP_LOGI(TAG_MQTT, "Subscribed. Message ID: %d", event->msg_id);
      break;
    case MQTT_EVENT_UNSUBSCRIBED:
      ESP_LOGI(TAG_MQTT, "Unsubscribed. Message ID: %d", event->msg_id);
      break;
    case MQTT_EVENT_PUBLISHED:
      ESP_LOGI(TAG_MQTT, "Published message ID: %d", event->msg_id);
      break;
    case MQTT_EVENT_DATA:
      ESP_LOGI(TAG_MQTT, "Data from topic [%.*s]: %.*s", event->topic_len, event->topic, event->data_len, event->data);
      break;
    case MQTT_EVENT_ERROR:
      ESP_LOGE(TAG_MQTT, "Error");
      break;
    default:
      ESP_LOGI(TAG_MQTT, "Other event id: %d", event->event_id);
      break;
  }
  return ESP_OK;
}

static void mqtt_event_handler(void *handler_args, esp_event_base_t base, int32_t event_id, void *event_data) {
  mqtt_event_handler_cb(event_data);
}

void mqtt_app_start(void) {
  mqtt_event_group = xEventGroupCreate();

  esp_mqtt_client_config_t mqtt_cfg = {
    .broker.address.uri = CONFIG_MQTT_BROKER_URI,
    .broker.address.port = 1883,
    .credentials.username = CONFIG_MQTT_USERNAME,
    .credentials.authentication.password = CONFIG_MQTT_PASSWORD
  };

  client = esp_mqtt_client_init(&mqtt_cfg);
  esp_mqtt_client_register_event(client, ESP_EVENT_ANY_ID, mqtt_event_handler, client);
  esp_mqtt_client_start(client);

  ESP_LOGI(TAG_MQTT, "Waiting for MQTT connection...");

  EventBits_t bits = xEventGroupWaitBits(mqtt_event_group,
                                        MQTT_CONNECTED_BIT,
                                        pdFALSE,
                                        pdTRUE,
                                        pdMS_TO_TICKS(10000));

  if (!(bits & MQTT_CONNECTED_BIT)) {
    ESP_LOGE(TAG_MQTT, "Failed to connect to MQTT broker");
  }

}

void mqtt_publish(const char *topic, const char *data) {
  esp_mqtt_client_publish(client, topic, data, 0, 1, 0);
}

void mqtt_subscribe(char *topic) {
  esp_mqtt_client_subscribe(client, topic, 0);
}

bool mqtt_is_connected(void) {
  return (xEventGroupGetBits(mqtt_event_group) & MQTT_CONNECTED_BIT) != 0;
}

void wait_mqtt_connection(const char* reason) {
  ESP_LOGW(TAG_MQTT, "Waiting for MQTT connection (%s)...", reason);

  xEventGroupWaitBits(mqtt_event_group,
                      MQTT_CONNECTED_BIT,
                      pdFALSE,
                      pdTRUE,
                      portMAX_DELAY);

  ESP_LOGI(TAG_MQTT, "MQTT reconnected.");
}