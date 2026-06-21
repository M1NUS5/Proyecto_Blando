package com.example.myapplication1

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

const val PATH_CONTROL_ENTRENAMIENTO = "/control_entrenamiento"

const val ACCION_INICIAR = "INICIAR"
const val ACCION_PAUSAR = "PAUSAR"
const val ACCION_REANUDAR = "REANUDAR"
const val ACCION_FINALIZAR = "FINALIZAR"

const val ORIGEN_TELEFONO = "TELEFONO"
const val ORIGEN_RELOJ = "RELOJ"

fun enviarComandoEntrenamiento(
    context: Context,
    accion: String,
    origen: String
) {
    val request = PutDataMapRequest.create(PATH_CONTROL_ENTRENAMIENTO).apply {
        dataMap.putString("accion", accion)
        dataMap.putString("origen", origen)
        dataMap.putLong("timestamp", System.currentTimeMillis())
        dataMap.putLong("nonce", System.nanoTime())
    }.asPutDataRequest().setUrgent()

    Wearable.getDataClient(context).putDataItem(request)
}