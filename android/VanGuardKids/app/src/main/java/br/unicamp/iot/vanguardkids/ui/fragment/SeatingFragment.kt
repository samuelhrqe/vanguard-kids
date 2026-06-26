package br.unicamp.iot.vanguardkids.ui.fragment

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import br.unicamp.iot.vanguardkids.R
import br.unicamp.iot.vanguardkids.data.mqtt.SEAT_IDS
import br.unicamp.iot.vanguardkids.data.mqtt.SeatReading
import br.unicamp.iot.vanguardkids.viewmodel.SeatingViewModel
import kotlinx.coroutines.launch
import java.util.Locale


class SeatingFragment : Fragment(R.layout.fragment_seating) {

    private val viewModel: SeatingViewModel by viewModels()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val tvMqttStatus = view.findViewById<TextView>(R.id.tvMqttStatus)

        val seatIcons = mapOf(
            "seat-01" to view.findViewById<ImageView>(R.id.ivSeat01),
            "seat-02" to view.findViewById<ImageView>(R.id.ivSeat02),
            "seat-03" to view.findViewById<ImageView>(R.id.ivSeat03),
            "seat-04" to view.findViewById<ImageView>(R.id.ivSeat04)
        )

        val seatTexts = mapOf(
            "seat-01" to view.findViewById<TextView>(R.id.tvSeat01),
            "seat-02" to view.findViewById<TextView>(R.id.tvSeat02),
            "seat-03" to view.findViewById<TextView>(R.id.tvSeat03),
            "seat-04" to view.findViewById<TextView>(R.id.tvSeat04)
        )

        val seatCards = mapOf(
            "seat-01" to view.findViewById<View>(R.id.cardSeat01),
            "seat-02" to view.findViewById<View>(R.id.cardSeat02),
            "seat-03" to view.findViewById<View>(R.id.cardSeat03),
            "seat-04" to view.findViewById<View>(R.id.cardSeat04)
        )

        val btnEndRoute = view.findViewById<Button>(R.id.btnEndRoute)
        val btnManageRoute = view.findViewById<Button>(R.id.btnManageRoute)


        btnManageRoute.visibility = View.GONE

        val tvRouteName = view.findViewById<TextView>(R.id.tvRouteName)
        val tvRouteStart = view.findViewById<TextView>(R.id.tvRouteStart)
        val tvRouteStops = view.findViewById<TextView>(R.id.tvRouteStops)
        val tvRouteSafety = view.findViewById<TextView>(R.id.tvRouteSafety)

        // Vincula a ação de controle de estado
        btnEndRoute.setOnClickListener { viewModel.handleRouteAction() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Fluxo 1: Estado dos sensores de peso nos bancos
                launch {
                    viewModel.uiState.collect { state ->
                        tvMqttStatus.text = state.mqttStatus

                        SEAT_IDS.forEach { seatId ->
                            val seatData = state.seats.getValue(seatId)
                            renderSeat(
                                seat = seatData,
                                icon = seatIcons.getValue(seatId),
                                text = seatTexts.getValue(seatId),
                                card = seatCards.getValue(seatId)
                            )
                        }
                    }
                }

                // Fluxo 2: Renderização
                // visual baseado nos Estados ("Stop", "In Progress", "Completed")
                launch {
                    viewModel.routeState.collect { route ->
                        tvRouteName.text = route.name
                        tvRouteStart.text = route.startTime
                        tvRouteStops.text = "${route.stopsCountText} paradas"
                        tvRouteSafety.text = route.safetyStatusText

                        // Atualiza dinamicamente as cores e textos do botão principal
                        when (route.state) {
                            "Stop" -> {
                                btnEndRoute.text = "INICIAR ROTA"
                                btnEndRoute.backgroundTintList = ColorStateList.valueOf(
                                    Color.parseColor("#4CAF50"))
                                btnEndRoute.isEnabled = true
                                btnEndRoute.alpha = 1.0f
                            }
                            "In Progress" -> {
                                btnEndRoute.text = "ENCERRAR ROTA"
                                btnEndRoute.backgroundTintList = ColorStateList.valueOf(
                                    Color.parseColor("#FF4B4B"))
                                btnEndRoute.isEnabled = true
                                btnEndRoute.alpha = 1.0f
                            }
                            "Completed" -> {
                                btnEndRoute.text = "ROTA FINALIZADA"
                                btnEndRoute.backgroundTintList = ColorStateList.valueOf(
                                    Color.parseColor("#78909C"))
                                btnEndRoute.isEnabled = false
                                btnEndRoute.alpha = 0.4f
                            }
                        }
                    }
                }

                // Fluxo 3: Eventos independentes de disparo único
                launch {
                    viewModel.uiEvent.collect { event ->
                        when (event) {
                            is SeatingViewModel.UiEvent.ShowSecurityDialog -> {
                                displaySafetyAlert(event.message)
                            }
                            is SeatingViewModel.UiEvent.ShowSuccessToast -> {
                                Toast.makeText(
                                    context,
                                    "Sucesso: Rota concluída e enviada ao Node-RED!",
                                    Toast.LENGTH_LONG).show()
                            }
                            is SeatingViewModel.UiEvent.ShowToast -> {
                                Toast.makeText(context,
                                    event.message,
                                    Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }

        viewModel.connectToVan()
    }

    private fun displaySafetyAlert(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("TRAVA DE SEGURANÇA INTERNA")
            .setMessage("$message\n\nA rota não pôde ser encerrada. " +
                    "Uma notificação crítica foi despachada para a central.")
            .setPositiveButton("OK", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun renderSeat(
        seat: SeatReading,
        icon: ImageView,
        text: TextView,
        card: View
    ) {
        val seatNumber = seat.seatId.removePrefix("seat-")
        val weight = seat.weightGrams
        val occupied = seat.isOccupied

        if (weight == null || occupied == null) {
            card.setBackgroundResource(R.drawable.bg_seat_card_unknown)
            icon.setImageResource(R.drawable.banco_verde)
            icon.alpha = 0.35f
            text.alpha = 0.7f
            text.text = "$seatNumber\nSem dados"
            return
        }

        icon.alpha = 1f
        text.alpha = 1f

        if (occupied) {
            card.setBackgroundResource(
                R.drawable.bg_seat_card_occupied
            )

            icon.setImageResource(R.drawable.banco_vermelho)

            text.text =
                "$seatNumber · ${formatWeight(weight)}\nOcupado"

        } else {
            card.setBackgroundResource(
                R.drawable.bg_seat_card_free
            )

            icon.setImageResource(R.drawable.banco_verde)

            text.text =
                "$seatNumber · ${formatWeight(weight)}\nLivre"
        }
    }

    private fun formatWeight(weightGrams: Double): String {
        return if (weightGrams >= 1000) {
            String.format(
                Locale("pt", "BR"),
                "%.1f kg",
                weightGrams / 1000.0
            )
        } else {
            String.format(
                Locale("pt", "BR"),
                "%.0f g",
                weightGrams
            )
        }
    }
}