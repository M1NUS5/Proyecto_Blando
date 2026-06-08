package com.example.myapplication1

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

const val PATH_CONFIG_APP = "/config_app"

fun enviarConfiguracionAlReloj(
    context: Context,
    settings: AppSettings
) {
    val request = PutDataMapRequest.create(PATH_CONFIG_APP).apply {
        dataMap.putBoolean("prediccionIAActiva", settings.prediccionIAActiva)
        dataMap.putBoolean("mostrarProbabilidades", settings.mostrarProbabilidades)
        dataMap.putBoolean("guardarUltimaCorrida", settings.guardarUltimaCorrida)
        dataMap.putBoolean("alertasRitmo", settings.alertasRitmo)
        dataMap.putBoolean("temaOscuro", settings.temaOscuro)
        dataMap.putString("unidadPrincipal", settings.unidadPrincipal)
        dataMap.putLong("timestamp", System.currentTimeMillis())
    }.asPutDataRequest()

    request.setUrgent()

    Wearable.getDataClient(context).putDataItem(request)
}