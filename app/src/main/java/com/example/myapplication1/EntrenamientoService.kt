package com.example.myapplication1

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Servicio en primer plano que mantiene el registro del entrenamiento cuando el
 * usuario sale de la aplicacion.
 *
 * Sin el, Android limita las actualizaciones de ubicacion en segundo plano y la
 * distancia se congela. Ademas publica una notificacion con tiempo, distancia y
 * pasos, leyendo las mismas fuentes que la pantalla principal.
 */
class EntrenamientoService : Service() {

    companion object {
        private const val CANAL_ID = "alyra_entrenamiento_activo"
        private const val NOTIFICACION_ID = 2601

        private const val ACCION_DETENER = "com.example.myapplication1.DETENER_ENTRENAMIENTO"

        /** Cada cuanto se refresca el texto de la notificacion. */
        private const val INTERVALO_ACTUALIZACION_MS = 1000L

        /**
         * Arranca el servicio; es seguro llamarlo varias veces.
         *
         * Se protege la llamada porque Android prohibe arrancar un servicio en
         * primer plano desde segundo plano, y el entrenamiento puede iniciarse
         * desde el reloj. En ese caso se pierde la notificacion, no la sesion.
         */
        fun iniciar(context: Context) {
            val intent = Intent(context, EntrenamientoService::class.java)

            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }.onFailure { error ->
                android.util.Log.w(
                    "EntrenamientoService",
                    "No se pudo iniciar el servicio en primer plano: ${error.message}"
                )
            }
        }

        /** Detiene el servicio y retira la notificacion. */
        fun detener(context: Context) {
            val intent = Intent(context, EntrenamientoService::class.java).apply {
                action = ACCION_DETENER
            }

            runCatching { context.startService(intent) }
        }
    }

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var actualizador: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        crearCanalDeNotificacion()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACCION_DETENER) {
            detenerServicio()
            return START_NOT_STICKY
        }

        iniciarEnPrimerPlano()
        iniciarActualizaciones()

        // START_STICKY pide al sistema que reinicie el servicio si tuviera que
        // liberarlo por falta de memoria, para no perder el entrenamiento.
        return START_STICKY
    }

    override fun onDestroy() {
        actualizador?.cancel()
        alcance.cancel()
        super.onDestroy()
    }

    // -----------------------------------------------------------------------
    // Ciclo de vida interno
    // -----------------------------------------------------------------------

    private fun iniciarEnPrimerPlano() {
        val tipo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }

        // ServiceCompat aplica la firma que corresponde a cada version de
        // Android; desde Android 14 es obligatorio declarar el tipo de servicio
        // tambien al momento de arrancarlo, no solo en el manifiesto.
        ServiceCompat.startForeground(
            this,
            NOTIFICACION_ID,
            construirNotificacion(),
            tipo
        )
    }

    private fun iniciarActualizaciones() {
        if (actualizador?.isActive == true) return

        actualizador = alcance.launch {
            while (isActive) {
                // Si el entrenamiento termino desde el reloj o desde otra
                // pantalla, el servicio se retira solo para no dejar una
                // notificacion huerfana.
                if (!hayEntrenamientoEnCurso()) {
                    detenerServicio()
                    return@launch
                }

                notificationManager()?.notify(NOTIFICACION_ID, construirNotificacion())
                delay(INTERVALO_ACTUALIZACION_MS)
            }
        }
    }

    private fun detenerServicio() {
        actualizador?.cancel()
        actualizador = null

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun hayEntrenamientoEnCurso(): Boolean {
        val estado = RunDataStore.estadoEntrenamiento
        return estado == "CORRIENDO" || estado == "PAUSADO"
    }

    // -----------------------------------------------------------------------
    // Notificacion
    // -----------------------------------------------------------------------

    private fun notificationManager(): NotificationManager? =
        ContextCompat.getSystemService(this, NotificationManager::class.java)

    private fun crearCanalDeNotificacion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val canal = NotificationChannel(
            CANAL_ID,
            "Entrenamiento en curso",
            NotificationManager.IMPORTANCE_LOW // sin sonido: se actualiza cada segundo
        ).apply {
            description = "Muestra el tiempo, la distancia y los pasos mientras entrenas."
            setShowBadge(false)
        }

        notificationManager()?.createNotificationChannel(canal)
    }

    private fun construirNotificacion(): android.app.Notification {
        val pausado = RunDataStore.estadoEntrenamiento == "PAUSADO"

        val intentApp = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val alTocar = PendingIntent.getActivity(
            this,
            0,
            intentApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CANAL_ID)
            .setSmallIcon(R.drawable.ic_notificacion_entrenamiento)
            .setContentTitle(
                if (pausado) "ALYRA · Entrenamiento en pausa" else "ALYRA · Entrenamiento en curso"
            )
            .setContentText(textoDeMetricas())
            .setStyle(NotificationCompat.BigTextStyle().bigText(textoDeMetricas()))
            .setContentIntent(alTocar)
            .setOngoing(true)          // el usuario no puede descartarla por error
            .setOnlyAlertOnce(true)    // no vibra ni suena en cada actualizacion
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** Ejemplo del texto resultante: `12:34  ·  2.45 km  ·  3210 pasos`. */
    private fun textoDeMetricas(): String {
        val datosReloj = DatosRelojStore.datos.value

        val distanciaKm = HomeSensorPrecisionUtils.seleccionarDistanciaSegura(
            distanciaTelefonoKm = RunDataStore.phoneDistanceMeters / 1000.0,
            distanciaRelojKm = datosReloj.distancia.toDouble(),
            pasos = datosReloj.pasos
        )

        return listOf(
            formatearTiempo(RunDataStore.obtenerTiempoActualSegundos()),
            String.format(Locale.US, "%.2f km", distanciaKm),
            "${datosReloj.pasos} pasos"
        ).joinToString("  ·  ")
    }

}
