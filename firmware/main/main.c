#include <stdio.h>
#include <string.h>
#include <stdbool.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_log.h"
#include "esp_err.h"
#include "esp_bt.h"
#include "esp_bt_main.h"
#include "esp_gap_ble_api.h"
#include "esp_gatts_api.h"
#include "esp_gatt_common_api.h"
#include "cJSON.h"

#define APP_ID 0x42
#define MAX_MSG 512
static const char *TAG = "blackvault_ble";
static uint16_t service_handle, rx_handle, tx_handle;
static esp_gatt_if_t gatts_if = ESP_GATT_IF_NONE;
static uint16_t conn_id;
static bool connected, notify_enabled;
static uint8_t tx_buffer[MAX_MSG];
static uint16_t tx_length;
static uint8_t service_uuid[16] = {0x54,0x4c,0x56,0x4c,0x56,0x42,0x7e,0x9c,0x91,0x4a,0x2d,0x6f,0x01,0x00,0x5a,0x7b};
static uint8_t rx_uuid[16] = {0x54,0x4c,0x56,0x4c,0x56,0x42,0x7e,0x9c,0x91,0x4a,0x2d,0x6f,0x02,0x00,0x5a,0x7b};
static uint8_t tx_uuid[16] = {0x54,0x4c,0x56,0x4c,0x56,0x42,0x7e,0x9c,0x91,0x4a,0x2d,0x6f,0x03,0x00,0x5a,0x7b};
static uint8_t primary_uuid[2] = {0x00, 0x28};
static uint8_t char_uuid[2] = {0x03, 0x28};
static uint8_t rx_decl[19] = {ESP_GATT_CHAR_PROP_BIT_WRITE | ESP_GATT_CHAR_PROP_BIT_WRITE_NR,0,0};
static uint8_t tx_decl[19] = {ESP_GATT_CHAR_PROP_BIT_READ | ESP_GATT_CHAR_PROP_BIT_NOTIFY,0,0};
static uint8_t rx_value[MAX_MSG];
static uint8_t cccd_value[2] = {0,0};
static esp_gatts_attr_db_t gatt_db[6];
static esp_gatt_srvc_id_t service_id;
static esp_ble_adv_params_t adv_params = {
    .adv_int_min = 0x40, .adv_int_max = 0x80,
    .adv_type = ADV_TYPE_IND, .own_addr_type = BLE_ADDR_TYPE_PUBLIC,
    .channel_map = ADV_CHNL_ALL, .adv_filter_policy = ADV_FILTER_ALLOW_SCAN_ANY_CON_ANY
};

static void respond(const char *request) {
    cJSON *root = cJSON_Parse(request);
    int id = 0;
    const char *cmd = NULL;
    if (root) {
        cJSON *v = cJSON_GetObjectItemCaseSensitive(root, "v");
        cJSON *idj = cJSON_GetObjectItemCaseSensitive(root, "id");
        cJSON *cmdj = cJSON_GetObjectItemCaseSensitive(root, "cmd");
        if (cJSON_IsNumber(idj)) id = idj->valueint;
        if (cJSON_IsString(cmdj)) cmd = cmdj->valuestring;
        if (!cJSON_IsNumber(v) || v->valueint != 0 || !cmd) {
            snprintf((char *)tx_buffer, sizeof(tx_buffer), "{\"v\":0,\"id\":%d,\"ok\":false,\"error\":\"bad_request\"}", id);
        } else if (!strcmp(cmd, "hello")) {
            snprintf((char *)tx_buffer, sizeof(tx_buffer), "{\"v\":0,\"id\":%d,\"ok\":true,\"device\":\"BlackVault\",\"fw\":\"0.2.0-ble-dev\",\"transport\":\"ble\",\"capabilities\":[\"identity\",\"status\"],\"security\":\"unauthenticated-dev\"}", id);
        } else if (!strcmp(cmd, "status") || !strcmp(cmd, "wallet_status")) {
            snprintf((char *)tx_buffer, sizeof(tx_buffer), "{\"v\":0,\"id\":%d,\"ok\":true,\"state\":\"uninitialized\",\"wallet_configured\":false,\"keys_ready\":false,\"signing_ready\":false,\"secure_session\":false}", id);
        } else if (!strcmp(cmd, "wallet_create") || !strcmp(cmd, "wallet_import")) {
            snprintf((char *)tx_buffer, sizeof(tx_buffer), "{\"v\":0,\"id\":%d,\"ok\":false,\"error\":\"secure_pairing_required\",\"message\":\"Seed setup disabled until authenticated encrypted pairing is implemented\"}", id);
        } else {
            snprintf((char *)tx_buffer, sizeof(tx_buffer), "{\"v\":0,\"id\":%d,\"ok\":false,\"error\":\"unknown_command\"}", id);
        }
        cJSON_Delete(root);
    } else snprintf((char *)tx_buffer, sizeof(tx_buffer), "{\"v\":0,\"id\":0,\"ok\":false,\"error\":\"invalid_json\"}");
    tx_length = (uint16_t)strlen((char *)tx_buffer);
    if (connected && notify_enabled && gatts_if != ESP_GATT_IF_NONE)
        esp_ble_gatts_send_indicate(gatts_if, conn_id, tx_handle, tx_length, tx_buffer, false);
}

static void gap_event(esp_gap_ble_cb_event_t event, esp_ble_gap_cb_param_t *param) {
    if (event == ESP_GAP_BLE_ADV_DATA_SET_COMPLETE_EVT) esp_ble_gap_start_advertising(&adv_params);
    else if (event == ESP_GAP_BLE_ADV_START_COMPLETE_EVT && param->adv_start_cmpl.status != ESP_BT_STATUS_SUCCESS)
        ESP_LOGE(TAG, "Advertising start failed: %d", param->adv_start_cmpl.status);
}

static void gatts_event(esp_gatts_cb_event_t event, esp_gatt_if_t interface, esp_ble_gatts_cb_param_t *param) {
    switch (event) {
    case ESP_GATTS_REG_EVT: {
        gatts_if = interface;
        esp_ble_gap_set_device_name("BlackVault");
        esp_ble_adv_data_t adv = {
            .set_scan_rsp = false, .include_name = true, .include_txpower = true,
            .min_interval = 0x20, .max_interval = 0x40, .appearance = 0,
            .manufacturer_len = 0, .p_manufacturer_data = NULL,
            .service_data_len = 0, .p_service_data = NULL,
            .service_uuid_len = sizeof(service_uuid), .p_service_uuid = service_uuid,
            .flag = ESP_BLE_ADV_FLAG_GEN_DISC | ESP_BLE_ADV_FLAG_BREDR_NOT_SPT
        };
        esp_ble_gap_config_adv_data(&adv);
        esp_ble_gatts_create_attr_tab(gatt_db, interface, 6, 0);
        break;
    }
    case ESP_GATTS_CREAT_ATTR_TAB_EVT:
        if (param->add_attr_tab.status == ESP_GATT_OK && param->add_attr_tab.num_handle == 6) {
            service_handle = param->add_attr_tab.handles[0];
            rx_handle = param->add_attr_tab.handles[2];
            tx_handle = param->add_attr_tab.handles[4];
            esp_ble_gatts_start_service(service_handle);
        } else ESP_LOGE(TAG, "GATT attribute table creation failed");
        break;
    case ESP_GATTS_CONNECT_EVT:
        connected = true; conn_id = param->connect.conn_id; notify_enabled = false;
        ESP_LOGI(TAG, "BLE client connected");
        break;
    case ESP_GATTS_DISCONNECT_EVT:
        connected = false; notify_enabled = false;
        esp_ble_gap_start_advertising(&adv_params);
        break;
    case ESP_GATTS_WRITE_EVT:
        if (param->write.handle == tx_handle + 1 && param->write.len >= 2) {
            notify_enabled = param->write.value[0] == 1;
        } else if (!param->write.is_prep && param->write.handle == rx_handle && param->write.len > 0 && param->write.len < MAX_MSG) {
            char request[MAX_MSG];
            memcpy(request, param->write.value, param->write.len);
            request[param->write.len] = '\0';
            respond(request);
        }
        break;
    case ESP_GATTS_READ_EVT:
        if (param->read.handle == tx_handle) {
            esp_gatt_rsp_t rsp = {0};
            rsp.attr_value.handle = param->read.handle;
            rsp.attr_value.len = tx_length;
            memcpy(rsp.attr_value.value, tx_buffer, tx_length);
            esp_ble_gatts_send_response(interface, param->read.conn_id, param->read.trans_id, ESP_GATT_OK, &rsp);
        }
        break;
    default: break;
    }
}

static void init_attribute_table(void) {
    memcpy(&rx_decl[3], rx_uuid, 16);
    memcpy(&tx_decl[3], tx_uuid, 16);
    gatt_db[0] = (esp_gatts_attr_db_t){ .attr_control = {.auto_rsp=ESP_GATT_AUTO_RSP}, .att_desc = {.uuid_length=ESP_UUID_LEN_16,.uuid_p=primary_uuid,.perm=ESP_GATT_PERM_READ,.max_length=16,.length=16,.value=service_uuid} };
    gatt_db[1] = (esp_gatts_attr_db_t){ .attr_control = {.auto_rsp=ESP_GATT_AUTO_RSP}, .att_desc = {.uuid_length=ESP_UUID_LEN_16,.uuid_p=char_uuid,.perm=ESP_GATT_PERM_READ,.max_length=19,.length=19,.value=rx_decl} };
    gatt_db[2] = (esp_gatts_attr_db_t){ .attr_control = {.auto_rsp=ESP_GATT_AUTO_RSP}, .att_desc = {.uuid_length=ESP_UUID_LEN_128,.uuid_p=rx_uuid,.perm=ESP_GATT_PERM_WRITE,.max_length=MAX_MSG,.length=0,.value=rx_value} };
    gatt_db[3] = (esp_gatts_attr_db_t){ .attr_control = {.auto_rsp=ESP_GATT_AUTO_RSP}, .att_desc = {.uuid_length=ESP_UUID_LEN_16,.uuid_p=char_uuid,.perm=ESP_GATT_PERM_READ,.max_length=19,.length=19,.value=tx_decl} };
    gatt_db[4] = (esp_gatts_attr_db_t){ .attr_control = {.auto_rsp=ESP_GATT_RSP_BY_APP}, .att_desc = {.uuid_length=ESP_UUID_LEN_128,.uuid_p=tx_uuid,.perm=ESP_GATT_PERM_READ,.max_length=MAX_MSG,.length=0,.value=NULL} };
    gatt_db[5] = (esp_gatts_attr_db_t){ .attr_control = {.auto_rsp=ESP_GATT_AUTO_RSP}, .att_desc = {.uuid_length=ESP_UUID_LEN_16,.uuid_p=(uint8_t[]){0x02,0x29},.perm=ESP_GATT_PERM_READ|ESP_GATT_PERM_WRITE,.max_length=2,.length=2,.value=cccd_value} };
    service_id.is_primary = true;
    service_id.id.inst_id = 0;
    service_id.id.uuid.len = ESP_UUID_LEN_128;
    memcpy(service_id.id.uuid.uuid.uuid128, service_uuid, 16);
}

void app_main(void) {
    ESP_LOGI(TAG, "BlackVault BLE transport; wallet creation/import disabled until secure pairing is implemented.");
    ESP_ERROR_CHECK(esp_bt_controller_mem_release(ESP_BT_MODE_CLASSIC_BT));
    esp_bt_controller_config_t bt_cfg = BT_CONTROLLER_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_bt_controller_init(&bt_cfg));
    ESP_ERROR_CHECK(esp_bt_controller_enable(ESP_BT_MODE_BLE));
    ESP_ERROR_CHECK(esp_bluedroid_init());
    ESP_ERROR_CHECK(esp_bluedroid_enable());
    init_attribute_table();
    ESP_ERROR_CHECK(esp_ble_gap_register_callback(gap_event));
    ESP_ERROR_CHECK(esp_ble_gatts_register_callback(gatts_event));
    ESP_ERROR_CHECK(esp_ble_gatts_app_register(APP_ID));
    ESP_ERROR_CHECK(esp_ble_gatt_set_local_mtu(247));
    while (1) vTaskDelay(pdMS_TO_TICKS(1000));
}
