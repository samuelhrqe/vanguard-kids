package br.unicamp.iot.vanguardkids.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    val uiState: StateFlow<SeatingMqttState> =
        repository.seatingState

    val routeState: StateFlow<MockRoute> = repository.routeState
    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()
    fun connectToVan() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.startSensors()
        }
    }

    // Gerencia o clique baseado no Estado Atual da Máquina de Estados
    fun handleRouteAction() {
        viewModelScope.launch {
            val currentState = routeState.value.state

            when (currentState) {
                "Stop" -> {
                    // Primeiro clique: Inicia o percurso
                    repository.startRoute()
                    _uiEvent.emit(UiEvent.ShowToast("Rota Iniciada com Sucesso!"))
                }

                "In Progress" -> {
                    // Segundo clique: Valida os sensores físicos de peso antes de fechar
                    val currentSeats = uiState.value.seats
                    val containsOccupants = currentSeats.values.any { it.isOccupied == true }

                    if (containsOccupants) {
                        val errorMsg = "Bloqueado: Criança a bordo!"
                        repository.updateRouteState(errorMsg, nextState = "In Progress")
                        repository.dispatchRouteTelemetry(isBlocked = true)
                        _uiEvent.emit(UiEvent.ShowSecurityDialog(errorMsg))
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

    sealed interface UiEvent {
        data class ShowSecurityDialog(val message: String) : UiEvent
        object ShowSuccessToast : UiEvent
        data class ShowToast(val message: String) : UiEvent
    }

    override fun onCleared() {
        repository.stopSensors()
        super.onCleared()
    }
}