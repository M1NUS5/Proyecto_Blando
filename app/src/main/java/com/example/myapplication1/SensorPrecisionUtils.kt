package com.example.myapplication1

import java.util.Locale
import kotlin.math.abs

object SensorPrecisionUtils {

    fun calcularCadencia(
        pasos: Int,
        tiempoSegundos: Long
    ): Double {
        if (pasos <= 0 || tiempoSegundos <= 0L) return 0.0

        val minutos = tiempoSegundos / 60.0

        if (minutos <= 0.0) return 0.0

        return pasos / minutos
    }

    fun calcularPaceSeguro(
        distanciaKm: Double,
        tiempoSegundos: Long,
        pasos: Int
    ): Double {
        if (tiempoSegundos < 15L) return 0.0
        if (distanciaKm < 0.03) return 0.0
        if (pasos <= 0) return 0.0

        val pace = (tiempoSegundos / 60.0) / distanciaKm

        if (pace < 2.5) return 0.0

        // Mismo techo que en HomeSensorPrecisionUtils: acompaña al rango con el
        // que se entreno la red (hasta 30 min/km). Con el limite anterior de 25
        // una caminata lenta se descartaba y la IA se quedaba sin ritmo.
        if (pace > 30.0) return 0.0

        return pace
    }

    fun paceTexto(
        pace: Double
    ): String {
        return if (pace > 0.0) {
            String.format(Locale.US, "%.2f", pace)
        } else {
            "--"
        }
    }

    fun distanciaTexto(
        distancia: Double
    ): String {
        return String.format(Locale.US, "%.2f", distancia)
    }

    fun aceleracionSuavizada(
        anterior: Float,
        nueva: Float
    ): Float {
        if (nueva < 0f) return anterior

        val valorLimpio = if (nueva > 6f) {
            anterior
        } else {
            nueva
        }

        return ((anterior * 0.75f) + (valorLimpio * 0.25f))
    }

    fun aceleracionTexto(
        aceleracion: Float
    ): String {
        val limpia = when {
            aceleracion < 0f -> 0f
            aceleracion > 6f -> 0f
            else -> aceleracion
        }

        return String.format(Locale.US, "%.2f", limpia)
    }

    fun puntoGpsValido(
        accuracy: Float,
        metrosEntrePuntos: Float,
        segundosEntrePuntos: Float,
        pasosActuales: Int
    ): Boolean {
        if (pasosActuales <= 0) return false

        if (accuracy > 18f) return false

        if (metrosEntrePuntos < 1.2f) return false

        if (metrosEntrePuntos > 25f) return false

        if (segundosEntrePuntos <= 0f) return false

        val velocidadMps = metrosEntrePuntos / segundosEntrePuntos

        if (velocidadMps < 0.35f) return false

        if (velocidadMps > 7.5f) return false

        return true
    }

    fun zonaPorSensores(
        bpm: Int,
        pasos: Int,
        tiempoSegundos: Long,
        aceleracion: Float
    ): String {
        val cadencia = calcularCadencia(
            pasos = pasos,
            tiempoSegundos = tiempoSegundos
        )

        if (pasos <= 0 && aceleracion < 0.70f) {
            return "Reposo"
        }

        if (cadencia in 1.0..85.0 && bpm < 120) {
            return "Caminando"
        }

        if (cadencia in 86.0..135.0 || bpm in 120..150) {
            return "Trotando"
        }

        if (cadencia > 135.0 || bpm > 150) {
            return "Corriendo"
        }

        return "Analizando"
    }

    fun datosSuficientesParaIA(
        bpm: Int,
        pasos: Int,
        tiempoSegundos: Long,
        aceleracion: Float
    ): Boolean {
        if (tiempoSegundos < 15L) return false
        if (bpm <= 0) return false
        if (aceleracion < 0f) return false

        return true
    }
}