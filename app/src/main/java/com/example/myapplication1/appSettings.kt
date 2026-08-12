package com.example.myapplication1

data class AppSettings(
    val prediccionIAActiva: Boolean = true,
    val mostrarProbabilidades: Boolean = true,
    val guardarUltimaCorrida: Boolean = true,
    val alertasRitmo: Boolean = true,
    val temaOscuro: Boolean = false,

    /**
     * Oculta las metricas que solo puede medir el reloj (pulso y aceleracion).
     *
     * Afecta unicamente a lo que se muestra: la medicion sigue detectando sola
     * si hay reloj, de modo que dejar esta opcion mal puesta nunca impide que la
     * aplicacion registre lo que si puede registrar.
     */
    val soloTelefono: Boolean = false,

    val unidadPrincipal: String = "kilometros",
    val ultimaActualizacion: Long = System.currentTimeMillis()
)