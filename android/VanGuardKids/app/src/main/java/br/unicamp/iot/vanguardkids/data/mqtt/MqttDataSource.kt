package br.unicamp.iot.vanguardkids.data.mqtt

import android.util.Log
import br.unicamp.iot.vanguardkids.BuildConfig
import com.android.identity.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject

class MqttDataSource {

    companion object {
        private const val TAG = "MQTT"

        private const val BROKER_URL = "tcp://192.168.18.15:1883"

        private const val SEATS_TOPIC_FILTER = "vanguard-kids/seats/+"
        private const val SEATS_TOPIC_PREFIX = "vanguard-kids/seats/"
        private const val HEARTBEAT_TOPIC = "vanguard-kids/heartbeat"
    }

    private val clientId = "vk-${UUID.randomUUID().toString().take(12)}"

    private var mqttClient: MqttClient? = null
    private var isConnecting = false

    private val _seatingState = MutableStateFlow(SeatingMqttState())

    val seatingState: StateFlow<SeatingMqttState> = _seatingState

    @Synchronized
    fun connect() {
        if (isConnecting || mqttClient?.isConnected == true) return

        isConnecting = true

        _seatingState.update {
            it.copy(mqttStatus = "Conectando ao broker...")
        }

        try {
            runCatching { mqttClient?.close() }

            val client = MqttClient(
                BROKER_URL,
                clientId,
                MemoryPersistence()
            )

            mqttClient = client

            client.setCallback(object : MqttCallback {

                override fun connectionLost(cause: Throwable?) {
                    Log.e(TAG, "Conexão MQTT perdida", cause)

                    _seatingState.update {
                        it.copy(mqttStatus = "Conexão MQTT perdida")
                    }
                }

                override fun messageArrived(
                    topic: String?,
                    message: MqttMessage?
                ) {
                    val receivedTopic = topic ?: return

                    val payload = message?.payload
                        ?.let { String(it, Charsets.UTF_8) }
                        .orEmpty()

                    when {
                        receivedTopic == HEARTBEAT_TOPIC -> {
                            handleHeartbeat(payload)
                        }

                        receivedTopic.startsWith(SEATS_TOPIC_PREFIX) -> {
                            handleSeatMessage(receivedTopic, payload)
                        }

                        else -> {
                            Log.w(TAG, "Tópico não reconhecido: $receivedTopic")
                        }
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
            })

            val options = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = 10
                keepAliveInterval = 20

                userName = BuildConfig.MQTT_USERNAME
                password = BuildConfig.MQTT_PASSWORD.toCharArray()
            }

            client.connect(options)

            client.subscribe(SEATS_TOPIC_FILTER, 1)
            client.subscribe(HEARTBEAT_TOPIC, 0)

            _seatingState.update {
                it.copy(mqttStatus = "Conectado ao broker")
            }

            Log.d(TAG, "MQTT conectado e inscrito nos tópicos")

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao conectar MQTT", e)

            _seatingState.update {
                it.copy(mqttStatus = "Erro de conexão MQTT")
            }

            runCatching { mqttClient?.close() }
            mqttClient = null

        } finally {
            isConnecting = false
        }
    }

    private fun handleHeartbeat(payload: String) {
        _seatingState.update {
            it.copy(
                mqttStatus = "Conectado ao broker",
                heartbeatPayload = payload
            )
        }
    }

    private fun handleSeatMessage(
        topic: String,
        payload: String
    ) {
        val seatIdFromTopic = topic.removePrefix(SEATS_TOPIC_PREFIX)

        if (seatIdFromTopic !in SEAT_IDS) {
            Log.w(TAG, "Assento ignorado: $seatIdFromTopic")
            return
        }

        try {
            val json = JSONObject(payload)

            val seatIdFromPayload = json.stringOrNull("seat")

            if (
                seatIdFromPayload != null &&
                seatIdFromPayload != seatIdFromTopic
            ) {
                Log.w(
                    TAG,
                    "Assento do JSON diferente do tópico. " +
                            "Tópico=$seatIdFromTopic | JSON=$seatIdFromPayload"
                )
            }

            val weightGrams = json.doubleOrNull("weight_g")

            val seatReading = SeatReading(
                seatId = seatIdFromTopic,
                clientId = json.stringOrNull("client_id"),
                adcRaw = json.intOrNull("adc_raw"),
                voltage = json.doubleOrNull("voltage"),
                weightGrams = weightGrams,

                // Regra da aplicação:
                // até 20 g = livre
                // acima de 20 g = ocupado
                isOccupied = weightGrams?.let {
                    it > FREE_SEAT_THRESHOLD_G
                },

                reportedOccupied = json.booleanOrNull("occupied"),
                timestampSeconds = json.longOrNull("ts"),
                receivedAtMillis = System.currentTimeMillis()
            )

            _seatingState.update { currentState ->
                currentState.copy(
                    mqttStatus = "Conectado ao broker",
                    seats = currentState.seats + (
                            seatIdFromTopic to seatReading
                            )
                )
            }

            Log.d(
                TAG,
                "Assento $seatIdFromTopic atualizado: $weightGrams g"
            )

        } catch (e: Exception) {
            Log.e(
                TAG,
                "JSON inválido recebido em $topic: $payload",
                e
            )
        }
    }

    @Synchronized
    fun disconnect() {
        val client = mqttClient
        mqttClient = null

        try {
            if (client?.isConnected == true) {
                client.disconnect()
            }

            client?.close()

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao desconectar MQTT", e)

        } finally {
            _seatingState.update {
                it.copy(mqttStatus = "Desconectado do broker")
            }
        }
    }

    private fun JSONObject.stringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null

        return optString(key)
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun JSONObject.intOrNull(key: String): Int? {
        if (!has(key) || isNull(key)) return null

        val value = optInt(key, Int.MIN_VALUE)

        return value.takeIf { it != Int.MIN_VALUE }
    }

    private fun JSONObject.longOrNull(key: String): Long? {
        if (!has(key) || isNull(key)) return null

        val value = optLong(key, Long.MIN_VALUE)

        return value.takeIf { it != Long.MIN_VALUE }
    }

    private fun JSONObject.doubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null

        val value = optDouble(key, Double.NaN)

        return value.takeIf { !it.isNaN() }
    }

    private fun JSONObject.booleanOrNull(key: String): Boolean? {
        if (!has(key) || isNull(key)) return null

        return try {
            getBoolean(key)
        } catch (_: Exception) {
            null
        }
    }
}