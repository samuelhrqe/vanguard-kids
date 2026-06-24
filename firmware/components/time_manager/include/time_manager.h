#ifndef TIME_MANAGER_H
#define TIME_MANAGER_H

#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>
#include <time.h>

#include "esp_log.h"
#include "esp_sntp.h"

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

void time_manager_init(void);

uint64_t get_epoch(void);

bool time_is_synced(void);

void get_timestamp(char *buffer, size_t buffer_size);

#endif