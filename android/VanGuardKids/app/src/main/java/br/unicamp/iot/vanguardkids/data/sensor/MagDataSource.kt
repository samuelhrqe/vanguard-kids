package br.unicamp.iot.vanguardkids.data.sensor

import android.os.IBinder
import android.util.Log
import com.android.mag_eclair.IMag
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MagDataSource {
    private val TAG = "MagDataSource"
    private var mMagService: IMag? = null

    init {
        try {
            // Usa reflection para buscar o serviço legado "mag_eclair" rodando no nível do sistema
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
            val binderMag = getServiceMethod.invoke(null, "mag_eclair") as IBinder?

            if (binderMag != null) {
                mMagService = IMag.Stub.asInterface(binderMag)
                Log.i(TAG, "Conectado ao mag_eclair com sucesso via AIDL!")
            } else {
                Log.e(TAG, "Serviço 'mag_eclair' não encontrado no Binder.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao conectar via reflection: ${e.message}")
        }
    }

    // Cria um fluxo de dados (Flow) contínuo para o ViewModel consumir
    fun getMagnetometerData(): Flow<Pair<Float, String>> = flow {
        while (true) {
            if (mMagService != null) {
                try {
                    // Correção: Chamada explícita ao método do AIDL
                    val azimuth = mMagService?.getAzimuth() ?: 0f
                    val direction = azimuthToDirection(azimuth)

                    // Emite o par contendo (Graus do Azimute, Ponto Cardeal/Colateral)
                    emit(Pair(azimuth, direction))
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao ler sensor: ${e.message}")
                }
            }
            delay(500L) // Aguarda 500ms (2Hz) antes da próxima leitura
        }
    }

    private fun azimuthToDirection(azimuth: Float): String {
        return when (azimuth) {
            in 337.5..360.0, in 0.0..22.5 -> "N"
            in 22.5..67.5 -> "NE"
            in 67.5..112.5 -> "L"
            in 112.5..157.5 -> "SE"
            in 157.5..202.5 -> "S"
            in 202.5..247.5 -> "SO"
            in 247.5..292.5 -> "O"
            in 292.5..337.5 -> "NO"
            else -> ""
        }
    }
}