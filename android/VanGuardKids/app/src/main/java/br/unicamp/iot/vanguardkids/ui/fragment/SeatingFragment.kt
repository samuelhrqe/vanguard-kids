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

        val tvMqttStatus =
            view.findViewById<TextView>(R.id.tvMqttStatus)

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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
        }

        viewModel.connectToVan()
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

        // Ainda não chegou uma leitura MQTT válida.
        if (weight == null || occupied == null) {
            card.setBackgroundResource(
                R.drawable.bg_seat_card_unknown
            )

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