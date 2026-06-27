package br.unicamp.iot.vanguardkids.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import br.unicamp.iot.vanguardkids.R
import br.unicamp.iot.vanguardkids.data.mqtt.SeatReading
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SafetyBottomSheet(private val occupiedSeats: List<SeatReading>) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_bottom_sheet_safety, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val container = view.findViewById<LinearLayout>(R.id.dynamicSeatsContainer)
        val btnDismiss = view.findViewById<Button>(R.id.btnDismissBottomSheet)

        // Limpa o contêiner (garantia caso a view seja recriada)
        container.removeAllViews()

        // Para cada assento na lista de ocupados, cria um novo "Card" visual
        occupiedSeats.forEach { seat ->
            val seatView = layoutInflater.
            inflate(R.layout.item_bottom_sheet_seat,
                container, false)

            val tvTitle = seatView.findViewById<TextView>(R.id.tvSheetSeatTitle)

            // Extrai apenas o número do assento (Ex: de "seat-01" para "01")
            val seatNumber = seat.seatId.removePrefix("seat-")
            tvTitle.text = "Assento $seatNumber — Ocupado"

            // Adiciona o card gerado na tela
            container.addView(seatView)
        }

        // Fecha a bandeja quando o motorista clicar em "ENTENDIDO"
        btnDismiss.setOnClickListener {
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)

        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)

            // 1. Força a bandeja a já nascer totalmente aberta
            behavior.state = BottomSheetBehavior.STATE_EXPANDED

            // 2. Impede que a bandeja tenha aquele estágio "pela metade"
            // se o usuário tentar arrastar
            behavior.skipCollapsed = true
        }
    }
}