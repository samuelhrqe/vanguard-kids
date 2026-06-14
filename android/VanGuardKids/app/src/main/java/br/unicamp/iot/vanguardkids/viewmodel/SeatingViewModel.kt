package br.unicamp.iot.vanguardkids.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.unicamp.iot.vanguardkids.repository.VanGuardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SeatingViewModel : ViewModel() {
    private val repository = VanGuardRepository()
    val sensorData = repository.mqttData

    fun connectToVan() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.startSensors()
        }
    }
    override fun onCleared() {
        super.onCleared()
        repository.stopSensors()
    }
}