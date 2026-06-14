package br.unicamp.iot.vanguardkids.ui.fragment

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import br.unicamp.iot.vanguardkids.R
import br.unicamp.iot.vanguardkids.viewmodel.SeatingViewModel
import kotlinx.coroutines.launch


class SeatingFragment : Fragment(R.layout.fragment_seating) {
    private val viewModel: SeatingViewModel by viewModels()
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvMqttData = view.findViewById<TextView>(R.id.tvMqttData)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sensorData.collect { dado ->
                    tvMqttData.text = dado
                }
            }
        }

        viewModel.connectToVan()
    }
}