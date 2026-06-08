package com.example.myapplication1

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object RunDataStore {

    var currentPace by mutableStateOf(0f)
    var currentTime by mutableStateOf(0f)
    var currentDistance by mutableStateOf(0f)

    var lastPace by mutableStateOf(0f)
    var lastTime by mutableStateOf(0f)
    var lastDistance by mutableStateOf(0f)

    var lastBpm by mutableStateOf(0f)
    var lastCadence by mutableStateOf(0f)
    var lastAcceleration by mutableStateOf(0f)
    var lastSteps by mutableStateOf(0)

    var hasFinishedRun by mutableStateOf(false)
    var isTracking by mutableStateOf(false)

    var estadoEntrenamiento by mutableStateOf("EN_ESPERA")

    var startTimeMs by mutableStateOf(0L)
    var currentTimeMs by mutableStateOf(0L)
    var accumulatedTimeMs by mutableStateOf(0L)

    var phoneDistanceMeters by mutableStateOf(0.0)

    fun iniciarNuevoEntrenamiento() {
        estadoEntrenamiento = "CORRIENDO"
        isTracking = true

        val ahora = System.currentTimeMillis()
        startTimeMs = ahora
        currentTimeMs = ahora
        accumulatedTimeMs = 0L

        phoneDistanceMeters = 0.0

        currentPace = 0f
        currentTime = 0f
        currentDistance = 0f

        hasFinishedRun = false
    }

    fun pausarEntrenamiento() {
        if (estadoEntrenamiento != "CORRIENDO") return

        val ahora = System.currentTimeMillis()

        if (startTimeMs > 0L) {
            accumulatedTimeMs += ahora - startTimeMs
        }

        currentTimeMs = ahora
        startTimeMs = 0L

        estadoEntrenamiento = "PAUSADO"
        isTracking = false
    }

    fun reanudarEntrenamiento() {
        if (estadoEntrenamiento != "PAUSADO") return

        val ahora = System.currentTimeMillis()

        startTimeMs = ahora
        currentTimeMs = ahora

        estadoEntrenamiento = "CORRIENDO"
        isTracking = true
    }

    fun finalizarEntrenamiento() {
        if (estadoEntrenamiento == "EN_ESPERA" || estadoEntrenamiento == "FINALIZADO") return

        val ahora = System.currentTimeMillis()

        if (estadoEntrenamiento == "CORRIENDO" && startTimeMs > 0L) {
            accumulatedTimeMs += ahora - startTimeMs
        }

        currentTimeMs = ahora
        startTimeMs = 0L

        estadoEntrenamiento = "FINALIZADO"
        isTracking = false
        hasFinishedRun = true
    }

    fun obtenerTiempoActualMs(): Long {
        return if (isTracking && startTimeMs > 0L) {
            accumulatedTimeMs + (System.currentTimeMillis() - startTimeMs)
        } else {
            accumulatedTimeMs
        }
    }

    fun obtenerTiempoActualSegundos(): Long {
        return (obtenerTiempoActualMs() / 1000L).coerceAtLeast(0L)
    }
}