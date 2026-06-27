package br.unicamp.iot.vanguardkids.ui.fragment

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import br.unicamp.iot.vanguardkids.R
import br.unicamp.iot.vanguardkids.data.mqtt.SEAT_IDS
import br.unicamp.iot.vanguardkids.viewmodel.SeatingViewModel
import kotlinx.coroutines.launch

class StatusFragment : Fragment(R.layout.fragment_status) {

    private val viewModel: SeatingViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Vincula os componentes visuais através dos novos IDs
        val tvOcupadosCount = view.findViewById<TextView>(R.id.tvOcupadosCount)
        val tvLivresCount = view.findViewById<TextView>(R.id.tvLivresCount)
        val tvTotalCount = view.findViewById<TextView>(R.id.tvTotalCount)

        // 2. Define dinamicamente o total de assentos baseado na constante do sistema
        val totalSeats = SEAT_IDS.size
        tvTotalCount.text = totalSeats.toString()

        // 3. Coleta o estado reativo do MQTT
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                viewModel.uiState.collect { state ->
                    // Filtra na coleção (Map) quantos assentos estão marcados como ocupados pelo ESP32
                    val ocupadosCount = state.seats.values.count { it.isOccupied == true }

                    // A quantidade de livres é a diferença matemática simples
                    val livresCount = totalSeats - ocupadosCount

                    // Atualiza os painéis numéricos grandes na tela
                    tvOcupadosCount.text = ocupadosCount.toString()
                    tvLivresCount.text = livresCount.toString()
                }
            }
        }
    }
}