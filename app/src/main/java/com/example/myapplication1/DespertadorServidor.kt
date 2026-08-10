package com.example.myapplication1

import android.util.Log
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Despierta el servidor al abrir la aplicacion.
 *
 * El plan de alojamiento suspende el servicio tras un rato sin uso y la primera
 * peticion tarda cerca de un minuto. Adelantar ese arranque evita esa espera.
 */
object DespertadorServidor {

    private const val TAG = "DespertadorServidor"

    @Volatile
    private var yaSolicitado = false

    /** Envia una sola peticion de calentamiento por sesion de la aplicacion. */
    fun despertar() {
        if (yaSolicitado) return
        yaSolicitado = true

        RetrofitClient.instance.health().enqueue(object : Callback<Map<String, String>> {
            override fun onResponse(
                call: Call<Map<String, String>>,
                response: Response<Map<String, String>>
            ) {
                Log.d(TAG, "Servidor listo (codigo ${response.code()})")
            }

            override fun onFailure(call: Call<Map<String, String>>, t: Throwable) {
                Log.d(TAG, "No se pudo adelantar el arranque: ${t.message}")
            }
        })
    }
}
