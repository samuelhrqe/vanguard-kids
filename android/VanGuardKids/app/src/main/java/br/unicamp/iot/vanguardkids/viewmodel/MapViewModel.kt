package br.unicamp.iot.vanguardkids.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.unicamp.iot.vanguardkids.repository.VanGuardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MapViewModel : ViewModel() {
    private val repository = VanGuardRepository()


    private val _compassData = MutableStateFlow(Pair(0f, "--"))
    val compassData: StateFlow<Pair<Float, String>> = _compassData

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        viewModelScope.launch {
            repository.getCompassData().collect { data ->
                _compassData.value = data
            }
        }
    }
}