package br.unicamp.iot.vanguardkids.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.unicamp.iot.vanguardkids.data.mqtt.SeatReading
import br.unicamp.iot.vanguardkids.data.mqtt.SeatingMqttState
import br.unicamp.iot.vanguardkids.repository.MockRoute
import br.unicamp.iot.vanguardkids.repository.VanGuardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SeatingViewModel : ViewModel() {

    private val repository = VanGuardRepository()
    val uiState: StateFlow<SeatingMqttState> = repository.seatingState
    val routeState: StateFlow<MockRoute> = repository.routeState
    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()
    fun connectToVan() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.startSensors()
        }
    }
    fun handleRouteAction() {
        viewModelScope.launch {
            val currentState = routeState.value.state

            when (currentState) {
                "Stop" -> {
                    repository.startRoute()
                    _uiEvent.emit(UiEvent.ShowToast("Rota Iniciada com Sucesso!"))
                }

                "In Progress" -> {
                    val currentSeats = uiState.value.seats

                    // Filtra e extrai a lista fr assentos ocupados
                    val occupiedSeatsList = currentSeats.values.filter { it.isOccupied == true }

                    if (occupiedSeatsList.isNotEmpty()) {
                        val errorMsg = "Bloqueado: Criança a bordo!"
                        repository.updateRouteState(errorMsg, nextState = "In Progress")
                        repository.dispatchRouteTelemetry(isBlocked = true)

                        // lista para o Fragment renderizar a Bandeja
                        _uiEvent.emit(UiEvent.ShowSecurityDialog(occupiedSeatsList))

                    } else {
                        val successMsg = "Rota finalizada"
                        repository.updateRouteState(successMsg, nextState = "Completed")
                        repository.dispatchRouteTelemetry(isBlocked = false)
                        _uiEvent.emit(UiEvent.ShowSuccessToast)
                    }
                }
            }
        }
    }

    // Dispara a lista de opções mockadas em memória para o Fragment exibir na tela flutuante
    fun manageRoute() {
        viewModelScope.launch {
            val routes = VanGuardRepository.AVAILABLE_ROUTES
            _uiEvent.emit(UiEvent.ShowRouteSelectionDialog(routes))
        }
    }

    // Aplica a nova rota selecionada no repositório e emite a confirmação
    fun onRouteSelected(route: MockRoute) {
        viewModelScope.launch {
            repository.selectRoute(route)
            _uiEvent.emit(UiEvent.ShowToast("Nova rota ativa: ${route.name}"))
        }
    }

    override fun onCleared() {
        repository.stopSensors()
        super.onCleared()
    }

    sealed interface UiEvent {
        // collections de dados dos assentos
        data class ShowSecurityDialog(val occupiedSeats: List<SeatReading>) : UiEvent
        // NOVO: Evento tipado para carregar a coleção de rotas
        data class ShowRouteSelectionDialog(val routes: List<MockRoute>) : UiEvent
        object ShowSuccessToast : UiEvent
        data class ShowToast(val message: String) : UiEvent
    }
}