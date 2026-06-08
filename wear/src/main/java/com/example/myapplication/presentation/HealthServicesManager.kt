package com.example.myapplication.presentation

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate

private const val TAG = "HealthServicesManager"

class HealthServicesManager(
    private val context: Context
) {

    private val exerciseClient = HealthServices.getClient(context).exerciseClient

    private var callbackActual: ExerciseUpdateCallback? = null
    private var ejercicioIniciado: Boolean = false

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
                val datosBpm = update.latestMetrics.getData(DataType.HEART_RATE_BPM)

                if (datosBpm.isNotEmpty()) {
                    val bpmActual = datosBpm.last().value.toInt()

                    if (bpmActual in 35..220) {
                        Log.d(TAG, "BPM real recibido: $bpmActual")
                        onBpm(bpmActual)
                        onEstado("BPM real")
                    }
                }
            }

            override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {
                Log.d(TAG, "Resumen de vuelta recibido")
            }

            override fun onAvailabilityChanged(
                dataType: DataType<*, *>,
                availability: Availability
            ) {
                if (dataType == DataType.HEART_RATE_BPM) {
                    Log.d(TAG, "Disponibilidad BPM: $availability")

                    val estado = availability.toString()

                    when {
                        estado.contains("AVAILABLE", ignoreCase = true) -> {
                            onEstado("Sensor cardiaco disponible")
                        }

                        estado.contains("ACQUIRING", ignoreCase = true) -> {
                            onEstado("Calculando BPM...")
                        }

                        estado.contains("UNAVAILABLE", ignoreCase = true) -> {
                            onEstado("BPM no disponible")
                        }

                        else -> {
                            onEstado("Esperando BPM del reloj")
                        }
                    }
                }
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