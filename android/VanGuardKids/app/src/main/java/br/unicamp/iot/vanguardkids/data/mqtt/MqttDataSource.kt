package br.unicamp.iot.vanguardkids.data.mqtt

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MqttDataSource {
    private val brokerUrl = "tcp://192.168.18.15:1883"
    private val clientId = "VanGuardAndroidApp"
    private val topic = "vanguard/peso"

    private var mqttClient: MqttClient? = null
    private val _mqttMessages = MutableStateFlow("Iniciando conexão...")
    val mqttMessages: StateFlow<String> = _mqttMessages

    fun connect() {
        try {
            mqttClient = MqttClient(brokerUrl, clientId, MemoryPersistence())
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = 10
                keepAliveInterval = 20
            }
            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.e("MQTT", "Conexão perdida", cause)
                    _mqttMessages.value = "Desconectado do Broker"
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val payload = message?.payload?.let { String(it) } ?: ""
                    Log.d("MQTT", "Mensagem recebida: $payload")
                    _mqttMessages.value = "Peso na poltrona: $payload"
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            mqttClient?.connect(options)
            Log.d("MQTT", "Conectado ao broker local!")
            mqttClient?.subscribe(topic)
        } catch (e: Exception) {
            Log.e("MQTT", "Erro ao conectar", e)
            _mqttMessages.value = "Erro de conexão com MQTT"
        }
    }
    fun disconnect() {
        try {
            mqttClient?.disconnect()
        } catch (e: Exception) {
            Log.e("MQTT", "Erro ao desconectar", e)
        }
    }
}