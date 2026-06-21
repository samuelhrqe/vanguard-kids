package br.unicamp.iot.vanguardkids.data.mqtt

const val FREE_SEAT_THRESHOLD_G = 5000.0

val SEAT_IDS = listOf(
    "seat-01",
    "seat-02",
    "seat-03",
    "seat-04"
)

data class SeatReading(
    val seatId: String,
    val clientId: String? = null,
    val adcRaw: Int? = null,
    val voltage: Double? = null,
    val weightGrams: Double? = null,

    // Calculado localmente pelo Android usando weight_g.
    val isOccupied: Boolean? = null,

    // Informação recebida do ESP32, mantida apenas para referência.
    val reportedOccupied: Boolean? = null,

    val timestampSeconds: Long? = null,
    val receivedAtMillis: Long? = null
)

data class SeatingMqttState(
    val mqttStatus: String = "Iniciando conexão...",
    val heartbeatPayload: String? = null,
    val seats: Map<String, SeatReading> =
        SEAT_IDS.associateWith { seatId ->
            SeatReading(seatId = seatId)
        }
)