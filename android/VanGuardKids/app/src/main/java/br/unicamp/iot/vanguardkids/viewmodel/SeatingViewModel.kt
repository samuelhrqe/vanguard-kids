package br.unicamp.iot.vanguardkids.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.unicamp.iot.vanguardkids.data.mqtt.SeatingMqttState
import br.unicamp.iot.vanguardkids.repository.VanGuardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SeatingViewModel : ViewModel() {

    private val repository = VanGuardRepository()

    val uiState: StateFlow<SeatingMqttState> =
        repository.seatingState

    fun connectToVan() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.startSensors()
        }
    }

    override fun onCleared() {
        repository.stopSensors()
        super.onCleared()
    }
}