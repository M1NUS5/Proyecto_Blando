package com.example.myapplication1

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val BASE_URL = "https://entrenador-ritmo-backend.onrender.com/"

    // Los tiempos por defecto de OkHttp (10 s) se quedan cortos para este backend:
    // el analisis de comida depende de Gemini, la subida envia la fotografia en
    // base64, y el servidor puede tardar en responder la primera peticion cuando
    // estuvo inactivo. Con margenes mas amplios se evitan errores de tiempo de
    // espera que el usuario percibiria como fallas de la aplicacion.
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
