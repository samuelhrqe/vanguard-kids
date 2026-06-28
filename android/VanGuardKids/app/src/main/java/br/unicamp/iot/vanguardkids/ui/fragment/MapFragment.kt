package br.unicamp.iot.vanguardkids.ui.fragment

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import br.unicamp.iot.vanguardkids.R
import br.unicamp.iot.vanguardkids.viewmodel.MapViewModel
import kotlinx.coroutines.launch
import java.util.Locale

class MapFragment : Fragment(R.layout.fragment_map) {

    private val viewModel: MapViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val imgCompass = view.findViewById<ImageView>(R.id.imgCompass)
        val tvAzimuthValue = view.findViewById<TextView>(R.id.tvAzimuthValue)

        // Observa os dados do ViewModel com segurança
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.compassData.collect { (azimuth, direction) ->
                    tvAzimuthValue.text = String.format(Locale.getDefault(), "Azimute: %.0f° %s", azimuth, direction)

                    // Rotaciona a seta (dependendo do ícone, pode ser necessário subtrair 90 graus)
                    imgCompass.rotation = azimuth
                }
            }
        }
    }
}