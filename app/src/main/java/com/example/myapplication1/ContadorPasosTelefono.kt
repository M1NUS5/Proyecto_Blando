package com.example.myapplication1

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Cuenta los pasos con los sensores del propio telefono.
 *
 * Permite entrenar sin reloj: sin pasos no habria distancia ni ritmo, porque
 * ambos calculos los exigen como confirmacion de movimiento real. Cuando hay
 * reloj se sigue prefiriendo el suyo, que va en la muneca y es mas fiel.
 */
class ContadorPasosTelefono(context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val contadorAcumulado = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val detectorPaso = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    /** Falso en telefonos sin sensor de pasos; entonces solo sirve el reloj. */
    val disponible: Boolean = contadorAcumulado != null || detectorPaso != null

    /** Pasos de este entrenamiento, ya descontados los previos del dispositivo. */
    var pasos by mutableIntStateOf(0)
        private set

    private var midiendo = false

    // TYPE_STEP_COUNTER cuenta desde que arranco el telefono, no desde que
    // empezo el entrenamiento, asi que se guarda su valor inicial para restarlo.
    private var baseAcumulado = -1
    private var pasosDelDetector = 0

    private val escucha = object : SensorEventListener {
        override fun onSensorChanged(evento: SensorEvent) {
            if (!midiendo) return

            when (evento.sensor.type) {
                Sensor.TYPE_STEP_COUNTER -> {
                    val total = evento.values.firstOrNull()?.toInt() ?: return

                    if (baseAcumulado < 0) baseAcumulado = total

                    val delEntrenamiento = (total - baseAcumulado).coerceAtLeast(0)

                    // El acumulado es mas preciso, pero llega con retraso: se
                    // toma el mayor para que la cuenta no retroceda en pantalla.
                    pasos = maxOf(delEntrenamiento, pasosDelDetector)
                }

                Sensor.TYPE_STEP_DETECTOR -> {
                    // Responde paso a paso, asi que la cifra avanza de inmediato
                    // mientras el contador acumulado se pone al dia.
                    pasosDelDetector += 1
                    pasos = maxOf(pasos, pasosDelDetector)
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, precision: Int) = Unit
    }

    /** Empieza a contar desde cero. */
    fun iniciar() {
        if (!disponible) return

        reiniciar()
        midiendo = true

        contadorAcumulado?.let {
            sensorManager.registerListener(escucha, it, SensorManager.SENSOR_DELAY_NORMAL, 0)
        }
        detectorPaso?.let {
            sensorManager.registerListener(escucha, it, SensorManager.SENSOR_DELAY_NORMAL, 0)
        }
    }

    /** Deja de recibir lecturas, conservando el total alcanzado. */
    fun detener() {
        if (!disponible) return

        midiendo = false
        runCatching { sensorManager.unregisterListener(escucha) }
    }

    /** Pausa la cuenta sin perder lo andado, para reanudar despues. */
    fun pausar() {
        midiendo = false
    }

    /** Reanuda tras una pausa continuando el conteo donde se quedo. */
    fun reanudar() {
        if (!disponible) return

        // Se olvida la referencia anterior: mientras estuvo pausado el sensor
        // siguio sumando pasos que no pertenecen al entrenamiento.
        baseAcumulado = -1
        pasosDelDetector = pasos
        midiendo = true
    }

    private fun reiniciar() {
        pasos = 0
        baseAcumulado = -1
        pasosDelDetector = 0
    }
}
