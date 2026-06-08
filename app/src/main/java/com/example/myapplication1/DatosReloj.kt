package com.example.myapplication1

data class DatosReloj(
    val bpm: Int = 0,
    val pasos: Int = 0,
    val distancia: Float = 0f,
    val aceleracion: Float = 0f,

    val tiempoSegundos: Long = 0L,
    val pace: Float = 0f,
    val cadencia: Float = 0f,

    val estadoEntrenamiento: String = "EN_ESPERA",
    val estadoIA: String = "Sin datos",
    val consejoIA: String = "Esperando datos del reloj",

    val timestamp: Long = 0L
)