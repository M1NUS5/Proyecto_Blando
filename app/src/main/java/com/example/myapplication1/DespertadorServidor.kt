package com.example.myapplication1

import android.util.Log
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Despierta el servidor por adelantado.
 *
 * El backend esta alojado en un plan que suspende el servicio despues de un
 * rato sin recibir peticiones. Al reactivarse, la primera peticion queda en
 * espera cerca de un minuto mientras el contenedor vuelve a arrancar: para el
 * usuario parece que la aplicacion se congelo o que fallo el analisis de la
 * fotografia, cuando en realidad el servidor solo estaba encendiendo.
 *
 * Enviar una peticion ligera en cuanto la aplicacion abre adelanta ese arranque
 * al momento en que el usuario todavia esta navegando, de modo que cuando llega
 * a iniciar sesion o a analizar una comida el servidor ya responde de inmediato.
 *
 * Es intencionalmente silencioso: si falla no se avisa ni se reintenta, porque
 * su unico proposito es ganar tiempo. Cualquier error real aparecera en la
 * peticion que el usuario si esta esperando.
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
