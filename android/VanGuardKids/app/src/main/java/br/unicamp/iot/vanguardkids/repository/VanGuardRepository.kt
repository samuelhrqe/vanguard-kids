package br.unicamp.iot.vanguardkids.repository

import br.unicamp.iot.vanguardkids.data.mqtt.MqttDataSource
import br.unicamp.iot.vanguardkids.data.mqtt.SeatingMqttState
import br.unicamp.iot.vanguardkids.data.sensor.MagDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
data class MockRoute(
    val name: String = "Rota da manhã",
    val startTime: String = "14:15",
    val stopsCountText: String = "4",
    val safetyStatusText: String = "Aguardando início",
    val state: String = "Stop" // Estados válidos: "Stop" | "In Progress" | "Completed"
)

class VanGuardRepository {

    private val mqttDataSource = MqttDataSource()
    private val magDataSource = MagDataSource()

    val seatingState: StateFlow<SeatingMqttState> = mqttDataSource.seatingState

    private val _routeState = MutableStateFlow(MockRoute())
    val routeState: StateFlow<MockRoute> = _routeState.asStateFlow()

    fun startSensors() {
        mqttDataSource.connect()
    }

    fun stopSensors() {
        mqttDataSource.disconnect()
    }

    // altera o estado para Em Percurso ("In Progress") e avisa o Node-RED
    fun startRoute() {
        _routeState.update {
            it.copy(safetyStatusText = "Em percurso...", state = "In Progress")
        }
        dispatchRouteTelemetry(isBlocked = false)
    }

    // Modificado para aceitar a transição dinâmica de estados lógicos
    fun updateRouteState(text: String, nextState: String) {
        _routeState.update { it.copy(safetyStatusText = text, state = nextState) }
    }

    fun dispatchRouteTelemetry(isBlocked: Boolean) {
        val route = _routeState.value
        mqttDataSource.publishRouteStatus(
            routeName = route.name,
            stopsCount = route.stopsCountText,
            state = route.state,
            isBlockedAttempt = isBlocked
        )
    }

    fun getCompassData(): Flow<Pair<Float, String>> {
        return magDataSource.getMagnetometerData()
    }
}