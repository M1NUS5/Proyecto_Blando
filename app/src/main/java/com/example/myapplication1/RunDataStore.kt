package com.example.myapplication1

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import com.google.android.gms.maps.model.LatLng

/**
 * Diferencia maxima que se acepta entre la marca de tiempo recibida del reloj y
 * el reloj del propio telefono. Mas alla, se asume que las horas no coinciden.
 */
private const val MAXIMO_DESFASE_ACEPTADO_MS = 10_000L

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
    var finishedPathPoints by mutableStateOf(emptyList<LatLng>())

    var estadoEntrenamiento by mutableStateOf("EN_ESPERA")

    var startTimeMs by mutableStateOf(0L)
    var currentTimeMs by mutableStateOf(0L)
    var accumulatedTimeMs by mutableStateOf(0L)

    var phoneDistanceMeters by mutableStateOf(0.0)

    /**
     * Instante que se toma como referencia para contar el tiempo.
     *
     * Cuando la orden viene del reloj llega con la marca de tiempo del momento
     * en que se pulso alli, no del momento en que el telefono la recibe. Usar esa
     * marca hace que ambos dispositivos midan desde el mismo punto de partida en
     * lugar de separarse por lo que tardo el mensaje en cruzar el Bluetooth, una
     * diferencia que en cada pausa y reanudacion se iba sumando a la anterior.
     *
     * Se acepta solo si es coherente con el reloj propio: si los dos equipos
     * tuvieran la hora muy distinta, fiarse de la ajena daria un tiempo absurdo,
     * asi que en ese caso se prefiere el momento actual.
     */
    private fun instanteValido(propuesto: Long): Long {
        val ahora = System.currentTimeMillis()

        if (propuesto <= 0L) return ahora
        if (propuesto > ahora) return ahora
        if (ahora - propuesto > MAXIMO_DESFASE_ACEPTADO_MS) return ahora

        return propuesto
    }

    fun iniciarNuevoEntrenamiento(instanteMs: Long = 0L) {
        estadoEntrenamiento = "CORRIENDO"
        isTracking = true

        val ahora = instanteValido(instanteMs)
        startTimeMs = ahora
        currentTimeMs = ahora
        accumulatedTimeMs = 0L

        phoneDistanceMeters = 0.0
        finishedPathPoints = emptyList()

        currentPace = 0f
        currentTime = 0f
        currentDistance = 0f

        hasFinishedRun = false
    }

    fun pausarEntrenamiento(instanteMs: Long = 0L) {
        if (estadoEntrenamiento != "CORRIENDO") return

        val ahora = instanteValido(instanteMs)

        if (startTimeMs > 0L) {
            accumulatedTimeMs += ahora - startTimeMs
        }

        currentTimeMs = ahora
        startTimeMs = 0L

        estadoEntrenamiento = "PAUSADO"
        isTracking = false
    }

    fun reanudarEntrenamiento(instanteMs: Long = 0L) {
        if (estadoEntrenamiento != "PAUSADO") return

        val ahora = instanteValido(instanteMs)

        startTimeMs = ahora
        currentTimeMs = ahora

        estadoEntrenamiento = "CORRIENDO"
        isTracking = true
    }

    fun finalizarEntrenamiento(instanteMs: Long = 0L) {
        if (estadoEntrenamiento == "EN_ESPERA" || estadoEntrenamiento == "FINALIZADO") return

        val ahora = instanteValido(instanteMs)

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