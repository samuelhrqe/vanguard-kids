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
    val name: String = "Selecione uma Rota...",
    val startTime: String = "--:--",
    val stopsCountText: String = "-",
    val safetyStatusText: String = "Selecione uma rota para começar",
    val state: String = "Unselected" // "Unselected" | "Stop" | "In Progress" | "Completed"
)

class VanGuardRepository {
    companion object {
        // Banco de dados mockado em memória com as opções de rotas para o motorista
        val AVAILABLE_ROUTES = listOf(
            MockRoute("Rota Centro - Manhã", "07:00", "5"),
            MockRoute("Rota Unicamp - Almoço", "11:30", "4"),
            MockRoute("Rota Norte - Tarde", "14:15", "8"),
            MockRoute("Rota Especial - Noite", "19:00", "6")
        )
    }

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

    fun selectRoute(route: MockRoute) {
        // Quando selecionado, passa do estado "Unselected" para "Stop" (Ativa o botão de Iniciar)
        _routeState.value = route.copy(safetyStatusText = "Aguardando início", state = "Stop")
        dispatchRouteTelemetry(isBlocked = false)
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
        // Só envia telemetria se houver uma rota selecionada de fato
        if (route.state != "Unselected") {
            mqttDataSource.publishRouteStatus(
                routeName = route.name,
                stopsCount = route.stopsCountText,
                state = route.state,
                isBlockedAttempt = isBlocked
            )
        }
    }

    fun getCompassData(): Flow<Pair<Float, String>> {
        return magDataSource.getMagnetometerData()
    }
}