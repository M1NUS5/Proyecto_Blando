package com.example.myapplication.presentation

import android.os.Handler
import android.os.Looper
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WearListenerService : WearableListenerService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        if (messageEvent.path == PATH_CONTROL_ENTRENAMIENTO) {
            val accion = String(messageEvent.data)

            mainHandler.post {
                when (accion) {
                    ACCION_INICIAR   -> WearRunState.iniciar()
                    ACCION_PAUSAR    -> WearRunState.pausar()
                    ACCION_REANUDAR  -> WearRunState.reanudar()
                    ACCION_FINALIZAR -> WearRunState.finalizar()
                }
            }
        }
    }
}