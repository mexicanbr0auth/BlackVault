#include <stdio.h>
#include <string.h>
#include <stdbool.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "driver/gpio.h"
#include "esp_log.h"
#include "cJSON.h"
#define CONFIRM_BUTTON GPIO_NUM_0
#define CANCEL_BUTTON GPIO_NUM_1
static const char *TAG = "blackvault";
static void reply(int id, const char *payload) { printf("{\"v\":0,\"id\":%d,%s}\n", id, payload); fflush(stdout); }
void app_main(void) {
    ESP_LOGI(TAG, "BlackVault dev scaffold: no keys/signing");
    gpio_config_t cfg = {
        .pin_bit_mask = (1ULL << CONFIRM_BUTTON) | (1ULL << CANCEL_BUTTON),
        .mode = GPIO_MODE_INPUT, .pull_up_en = GPIO_PULLUP_ENABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE, .intr_type = GPIO_INTR_DISABLE
    };
    ESP_ERROR_CHECK(gpio_config(&cfg));
    char line[512]; size_t used = 0;
    while (1) {
        int c = getchar();
        if (c == EOF) { vTaskDelay(pdMS_TO_TICKS(10)); continue; }
        if (c == '\r') continue;
        if (c != '\n') {
            if (used < sizeof(line) - 1) line[used++] = (char)c;
            else { used = 0; reply(0, "\"ok\":false,\"error\":\"line_too_long\""); }
            continue;
        }
        line[used] = '\0'; used = 0;
        cJSON *root = cJSON_Parse(line);
        if (!root) { reply(0, "\"ok\":false,\"error\":\"invalid_json\""); continue; }
        cJSON *cmd = cJSON_GetObjectItemCaseSensitive(root, "cmd");
        cJSON *idj = cJSON_GetObjectItemCaseSensitive(root, "id");
        cJSON *vj = cJSON_GetObjectItemCaseSensitive(root, "v");
        int id = cJSON_IsNumber(idj) ? idj->valueint : 0;
        if (!cJSON_IsNumber(vj) || vj->valueint != 0 || !cJSON_IsString(cmd))
            reply(id, "\"ok\":false,\"error\":\"bad_request\"");
        else if (strcmp(cmd->valuestring, "hello") == 0) {
            printf("{\"v\":0,\"id\":%d,\"ok\":true,\"device\":\"BlackVault\",\"fw\":\"0.1.0-dev\",\"network\":\"bitcoin\",\"capabilities\":[\"identity\",\"status\"]}\n", id);
            fflush(stdout);
        } else if (strcmp(cmd->valuestring, "status") == 0) {
            bool a = gpio_get_level(CONFIRM_BUTTON) != 0, b = gpio_get_level(CANCEL_BUTTON) != 0;
            printf("{\"v\":0,\"id\":%d,\"ok\":true,\"state\":\"idle\",\"confirm_released\":%s,\"cancel_released\":%s}\n", id, a ? "true" : "false", b ? "true" : "false");
            fflush(stdout);
        } else reply(id, "\"ok\":false,\"error\":\"unknown_command\"");
        cJSON_Delete(root);
    }
}
