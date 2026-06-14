package br.unicamp.iot.vanguardkids.repository

import br.unicamp.iot.vanguardkids.data.mqtt.MqttDataSource
import kotlinx.coroutines.flow.StateFlow

class VanGuardRepository {
    private val mqttDataSource = MqttDataSource()
    val mqttData: StateFlow<String> = mqttDataSource.mqttMessages

    fun startSensors() {
        mqttDataSource.connect()
    }

    fun stopSensors() {
        mqttDataSource.disconnect()
    }
}