package com.example.myapplication1

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WearDataListenerService : WearableListenerService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        if (messageEvent.path == PATH_CONTROL_ENTRENAMIENTO) {
            val accion = String(messageEvent.data)
            Log.d("WearService", "Comando recibido del reloj: $accion")

            mainHandler.post {
                when (accion) {
                    ACCION_INICIAR   -> RunDataStore.iniciarNuevoEntrenamiento()
                    ACCION_PAUSAR    -> RunDataStore.pausarEntrenamiento()
                    ACCION_REANUDAR  -> RunDataStore.reanudarEntrenamiento()
                    ACCION_FINALIZAR -> RunDataStore.finalizarEntrenamiento()
                }
            }
        }
    }

       override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)

        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val item = event.dataItem

                if (item.uri.path == "/datos_entrenamiento") {
                    val dataMap = DataMapItem.fromDataItem(item).dataMap

                    val datos = DatosReloj(
                        bpm        = dataMap.getInt("bpm"),
                        pasos      = dataMap.getInt("pasos"),
                        distancia  = dataMap.getFloat("distancia"),
                        aceleracion = dataMap.getFloat("aceleracion"),

                        tiempoSegundos = dataMap.getLong("tiempoSegundos"),
                        pace           = dataMap.getFloat("pace"),
                        cadencia       = dataMap.getFloat("cadencia"),

                        estadoEntrenamiento = dataMap.getString("estadoEntrenamiento") ?: "EN_ESPERA",
                        estadoIA            = dataMap.getString("estadoIA") ?: "Sin datos",
                        consejoIA           = dataMap.getString("consejoIA") ?: "Sin consejo",

                        timestamp = dataMap.getLong("timestamp")
                    )

                    DatosRelojStore.actualizar(datos)
                    Log.d("WearData", "Datos recibidos: $datos")
                }
            }
        }
    }
}