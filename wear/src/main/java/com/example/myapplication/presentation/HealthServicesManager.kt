package com.example.myapplication.presentation

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.data.HeartRateAccuracy

private const val TAG = "HealthServicesManager"

class HealthServicesManager(
    private val context: Context
) {

    private val exerciseClient = HealthServices.getClient(context).exerciseClient

    private var callbackActual: ExerciseUpdateCallback? = null
    private var ejercicioIniciado: Boolean = false

    /**
     * Indica si el sensor optico esta entregando mediciones confiables.
     * Arranca en falso: las primeras lecturas, mientras el sensor se acopla a
     * la muneca, suelen ser imprecisas y no deben mostrarse como definitivas.
     */
    private var sensorDisponible: Boolean = false

    fun iniciarEjercicio(
        onBpm: (Int) -> Unit,
        onEstado: (String) -> Unit
    ) {
        if (ejercicioIniciado) {
            onEstado("Sensor cardiaco activo")
            return
        }

        onEstado("Iniciando sensor cardiaco...")

        val callback = object : ExerciseUpdateCallback {

            override fun onRegistered() {
                Log.d(TAG, "Callback registrado correctamente")
                onEstado("Sensor cardiaco registrado")
            }

            override fun onRegistrationFailed(throwable: Throwable) {
                Log.e(TAG, "Error registrando callback", throwable)
                onEstado("Error registrando sensor cardiaco")
            }

            override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
                // El sensor optico entrega lecturas incluso cuando el reloj esta
                // flojo o recien colocado, y en esos momentos son poco confiables.
                // Solo se aceptan cuando el propio sistema declara la medicion
                // como disponible; mientras se estabiliza se avisa al usuario en
                // lugar de mostrar un valor que puede estar equivocado.
                if (!sensorDisponible) {
                    onEstado("Estabilizando sensor cardiaco...")
                    return
                }

                val datosBpm = update.latestMetrics.getData(DataType.HEART_RATE_BPM)

                if (datosBpm.isEmpty()) return

                // Cada muestra viene acompanada de la calidad con la que el
                // reloj la midio. Es la misma valoracion que usa el sistema para
                // decidir si un dato es confiable, y descartarla obligaba a
                // adivinar despues cuales lecturas eran malas.
                val confiables = datosBpm.filter { muestra ->
                    when ((muestra.accuracy as? HeartRateAccuracy)?.sensorStatus) {
                        HeartRateAccuracy.SensorStatus.ACCURACY_HIGH,
                        HeartRateAccuracy.SensorStatus.ACCURACY_MEDIUM -> true

                        // Sin informacion de calidad se acepta: no todos los
                        // relojes la reportan y es preferible a quedarse sin dato.
                        null -> true

                        else -> false
                    }
                }

                if (confiables.isEmpty()) {
                    Log.d(TAG, "Lecturas descartadas por baja precision del sensor")
                    onEstado("Mejorando lectura cardiaca...")
                    return
                }

                val bpmActual = confiables.last().value.toInt()

                if (bpmActual in 35..220) {
                    Log.d(TAG, "BPM confiable recibido: $bpmActual")
                    onBpm(bpmActual)
                    onEstado("BPM real")
                }
            }

            override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {
                Log.d(TAG, "Resumen de vuelta recibido")
            }

            override fun onAvailabilityChanged(
                dataType: DataType<*, *>,
                availability: Availability
            ) {
                if (dataType != DataType.HEART_RATE_BPM) return

                Log.d(TAG, "Disponibilidad BPM: $availability")

                // Se compara contra el valor real y no contra su texto: la
                // cadena "UNAVAILABLE" contiene "AVAILABLE", por lo que una
                // comparacion por texto daba por disponible un sensor que en
                // realidad no lo estaba.
                sensorDisponible = availability == DataTypeAvailability.AVAILABLE

                val mensaje = when (availability) {
                    DataTypeAvailability.AVAILABLE -> "BPM real"
                    DataTypeAvailability.ACQUIRING -> "Calculando BPM..."
                    DataTypeAvailability.UNAVAILABLE -> "Ajusta el reloj a tu muñeca"
                    DataTypeAvailability.UNAVAILABLE_DEVICE_OFF_BODY -> "El reloj no está puesto"
                    else -> "Esperando BPM del reloj"
                }

                onEstado(mensaje)
            }
        }

        callbackActual = callback

        exerciseClient.setUpdateCallback(callback)

        val config = ExerciseConfig(
            exerciseType = ExerciseType.RUNNING,
            dataTypes = setOf(
                DataType.HEART_RATE_BPM
            ),
            isAutoPauseAndResumeEnabled = false,
            isGpsEnabled = false
        )

        val future = exerciseClient.startExerciseAsync(config)

        future.addListener(
            {
                try {
                    future.get()

                    ejercicioIniciado = true

                    Log.d(TAG, "Ejercicio iniciado correctamente")
                    onEstado("Midiendo BPM...")

                } catch (e: Exception) {
                    Log.e(TAG, "Error iniciando ejercicio", e)
                    ejercicioIniciado = false
                    limpiarCallback()
                    onEstado("Error iniciando BPM")
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun pausarEjercicio() {
        if (!ejercicioIniciado) return

        val future = exerciseClient.pauseExerciseAsync()

        future.addListener(
            {
                try {
                    future.get()
                    Log.d(TAG, "Ejercicio pausado correctamente")
                } catch (e: Exception) {
                    Log.e(TAG, "Error pausando ejercicio", e)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun reanudarEjercicio() {
        if (!ejercicioIniciado) return

        val future = exerciseClient.resumeExerciseAsync()

        future.addListener(
            {
                try {
                    future.get()
                    Log.d(TAG, "Ejercicio reanudado correctamente")
                } catch (e: Exception) {
                    Log.e(TAG, "Error reanudando ejercicio", e)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun finalizarEjercicio() {
        sensorDisponible = false

        if (!ejercicioIniciado) {
            limpiarCallback()
            return
        }

        val future = exerciseClient.endExerciseAsync()

        future.addListener(
            {
                try {
                    future.get()
                    Log.d(TAG, "Ejercicio finalizado correctamente")
                } catch (e: Exception) {
                    Log.e(TAG, "Error finalizando ejercicio", e)
                }

                ejercicioIniciado = false
                limpiarCallback()
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private fun limpiarCallback() {
        val callback = callbackActual ?: return

        val future = exerciseClient.clearUpdateCallbackAsync(callback)

        future.addListener(
            {
                try {
                    future.get()
                    Log.d(TAG, "Callback limpiado correctamente")
                } catch (e: Exception) {
                    Log.e(TAG, "Error limpiando callback", e)
                }

                callbackActual = null
            },
            ContextCompat.getMainExecutor(context)
        )
    }
}