package br.unicamp.iot.vanguardkids.ui.fragment

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import br.unicamp.iot.vanguardkids.R
import br.unicamp.iot.vanguardkids.data.mqtt.SEAT_IDS
import br.unicamp.iot.vanguardkids.data.mqtt.SeatReading
import br.unicamp.iot.vanguardkids.repository.MockRoute
import br.unicamp.iot.vanguardkids.viewmodel.SeatingViewModel
import kotlinx.coroutines.launch
import java.util.Locale


class SeatingFragment : Fragment(R.layout.fragment_seating) {
    private val viewModel: SeatingViewModel by activityViewModels()

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

        val tvRouteName = view.findViewById<TextView>(R.id.tvRouteName)
        val tvRouteStart = view.findViewById<TextView>(R.id.tvRouteStart)
        val tvRouteStops = view.findViewById<TextView>(R.id.tvRouteStops)
        val tvRouteSafety = view.findViewById<TextView>(R.id.tvRouteSafety)

        // Atribuição das ações de clique
        btnEndRoute.setOnClickListener { viewModel.handleRouteAction() }
        btnManageRoute.setOnClickListener { viewModel.manageRoute() }

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

                // Fluxo 2: Renderização visual baseada na Máquina de Estados da Rota
                launch {
                    viewModel.routeState.collect { route ->
                        tvRouteName.text = route.name
                        tvRouteStart.text = route.startTime

                        // Formata a string de paradas considerando o placeholder "-"
                        tvRouteStops.text = if (route.stopsCountText == "-") "-" else "${route.stopsCountText} paradas"
                        tvRouteSafety.text = route.safetyStatusText

                        when (route.state) {
                            "Unselected" -> {
                                // Estado inicial com o botão bloqueado aguardando seleção da rota
                                btnEndRoute.text = "INICIAR ROTA"
                                btnEndRoute.backgroundTintList = ColorStateList.valueOf(
                                    Color.parseColor("#78909C")) // Cinza Neutro
                                btnEndRoute.isEnabled = false
                                btnEndRoute.alpha = 0.5f

                                btnManageRoute.isEnabled = true
                                btnManageRoute.alpha = 1.0f
                            }
                            "Stop" -> {
                                // Habilita o botão verde para iniciar após o motorista selecionar a rota
                                btnEndRoute.text = "INICIAR ROTA"
                                btnEndRoute.backgroundTintList = ColorStateList.valueOf(
                                    Color.parseColor("#4CAF50")) // Verde
                                btnEndRoute.isEnabled = true
                                btnEndRoute.alpha = 1.0f

                                btnManageRoute.isEnabled = true
                                btnManageRoute.alpha = 1.0f
                            }
                            "In Progress" -> {
                                btnEndRoute.text = "ENCERRAR ROTA"
                                btnEndRoute.backgroundTintList = ColorStateList.valueOf(
                                    Color.parseColor("#FF4B4B")) // Vermelho
                                btnEndRoute.isEnabled = true
                                btnEndRoute.alpha = 1.0f

                                btnManageRoute.isEnabled = false
                                btnManageRoute.alpha = 0.4f
                            }
                            "Completed" -> {
                                btnEndRoute.text = "ROTA FINALIZADA"
                                btnEndRoute.backgroundTintList = ColorStateList.valueOf(
                                    Color.parseColor("#78909C")) // Cinza
                                btnEndRoute.isEnabled = false
                                btnEndRoute.alpha = 0.4f

                                btnManageRoute.isEnabled = true
                                btnManageRoute.alpha = 1.0f
                            }
                        }
                    }
                }

                // Fluxo 3: Eventos independentes de disparo único (Dialogs e Toasts)
                launch {
                    viewModel.uiEvent.collect { event ->
                        when (event) {
                            is SeatingViewModel.UiEvent.ShowSecurityDialog -> {
                                displaySafetyAlert(event.message)
                            }
                            is SeatingViewModel.UiEvent.ShowRouteSelectionDialog -> {
                                openRouteSelectionDialog(event.routes)
                            }
                            is SeatingViewModel.UiEvent.ShowSuccessToast -> {
                                Toast.makeText(
                                    context,
                                    "Sucesso: Rota concluída e enviada ao Node-RED!",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            is SeatingViewModel.UiEvent.ShowToast -> {
                                Toast.makeText(
                                    context, event.message,
                                    Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }

        viewModel.connectToVan()
    }

    private fun openRouteSelectionDialog(routes: List<MockRoute>) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_manage_route, null)

        val routesContainer = dialogView.findViewById<LinearLayout>(R.id.routesContainer)
        val btnCancelDialog = dialogView.findViewById<Button>(R.id.btnCancelDialog)

        val alertDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        routes.forEach { route ->
            val routeButton = Button(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 16)
                }
                text = "${route.name} (${route.startTime})"
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3A4454"))
                setTextColor(Color.WHITE)
                isAllCaps = false

                setOnClickListener {
                    viewModel.onRouteSelected(route)
                    alertDialog.dismiss()
                }
            }
            routesContainer.addView(routeButton)
        }

        btnCancelDialog.setOnClickListener { alertDialog.dismiss() }
        alertDialog.show()
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
        val occupied = seat.isOccupied

        // Se ainda não tiver chegado leitura (null), mantém o layout XML padrão de Livre.
        if (occupied == null) return

        if (occupied) {
            card.setBackgroundResource(R.drawable.bg_seat_card_occupied)
            icon.setImageResource(R.drawable.banco_vermelho)
            text.text = "$seatNumber\nOcupado"
        } else {
            card.setBackgroundResource(R.drawable.bg_seat_card_free)
            icon.setImageResource(R.drawable.banco_verde)
            text.text = "$seatNumber\nLivre"
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