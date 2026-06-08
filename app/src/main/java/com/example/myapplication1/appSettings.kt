package com.example.myapplication1

data class AppSettings(
    val prediccionIAActiva: Boolean = true,
    val mostrarProbabilidades: Boolean = true,
    val guardarUltimaCorrida: Boolean = true,
    val alertasRitmo: Boolean = true,
    val temaOscuro: Boolean = false,
    val unidadPrincipal: String = "kilometros",
    val ultimaActualizacion: Long = System.currentTimeMillis()
)