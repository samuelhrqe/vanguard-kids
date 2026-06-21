package br.unicamp.iot.vanguardkids.repository

import br.unicamp.iot.vanguardkids.data.mqtt.MqttDataSource
import br.unicamp.iot.vanguardkids.data.mqtt.SeatingMqttState
import br.unicamp.iot.vanguardkids.data.sensor.MagDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class VanGuardRepository {

    private val mqttDataSource = MqttDataSource()

    val seatingState: StateFlow<SeatingMqttState> =
        mqttDataSource.seatingState

    fun startSensors() {
        mqttDataSource.connect()
    }

    fun stopSensors() {
        mqttDataSource.disconnect()
    }

    private val magDataSource = MagDataSource()

    fun getCompassData(): Flow<Pair<Float, String>> {
        return magDataSource.getMagnetometerData()
    }
}