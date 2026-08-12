package com.example.myapplication.presentation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.R

/**
 * Servicio en primer plano del reloj.
 *
 * Wear OS suspende la aplicacion al apagarse la pantalla y los sensores dejan de
 * reportar, asi que un entrenamiento con la muneca abajo se quedaria sin pasos
 * ni distancia. Se resuelve con el servicio en primer plano mas un
 * `PARTIAL_WAKE_LOCK`, que se libera siempre al detenerlo.
 */
class EntrenamientoWearService : Service() {

    companion object {
        private const val CANAL_ID = "alyra_entrenamiento_reloj"
        private const val NOTIFICACION_ID = 3701

        private const val ACCION_DETENER = "com.example.myapplication.DETENER_ENTRENAMIENTO_RELOJ"
        private const val ETIQUETA_WAKELOCK = "ALYRA:entrenamiento"

        /** Limite de seguridad del bloqueo: cuatro horas de entrenamiento. */
        private const val MAXIMO_WAKELOCK_MS = 4L * 60L * 60L * 1000L

        fun iniciar(context: Context) {
            val intent = Intent(context, EntrenamientoWearService::class.java)

            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }.onFailure { error ->
                android.util.Log.w(
                    "EntrenamientoWear",
                    "No se pudo iniciar el servicio: ${error.message}"
                )
            }
        }

        fun detener(context: Context) {
            val intent = Intent(context, EntrenamientoWearService::class.java).apply {
                action = ACCION_DETENER
            }

            runCatching { context.startService(intent) }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

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
        adquirirWakeLock()

        return START_STICKY
    }

    override fun onDestroy() {
        liberarWakeLock()
        super.onDestroy()
    }

    // -----------------------------------------------------------------------
    // Primer plano y bloqueo de procesador
    // -----------------------------------------------------------------------

    private fun iniciarEnPrimerPlano() {
        val tipo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICACION_ID,
            construirNotificacion(),
            tipo
        )
    }

    private fun adquirirWakeLock() {
        if (wakeLock?.isHeld == true) return

        val powerManager = ContextCompat.getSystemService(this, PowerManager::class.java)

        wakeLock = powerManager
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, ETIQUETA_WAKELOCK)
            ?.apply {
                setReferenceCounted(false)
                // El tiempo maximo evita que un fallo deje el bloqueo activo de
                // forma indefinida agotando la bateria del reloj.
                acquire(MAXIMO_WAKELOCK_MS)
            }
    }

    private fun liberarWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }

        wakeLock = null
    }

    private fun detenerServicio() {
        liberarWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // -----------------------------------------------------------------------
    // Notificacion
    // -----------------------------------------------------------------------

    private fun crearCanalDeNotificacion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val canal = NotificationChannel(
            CANAL_ID,
            "Entrenamiento en curso",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mantiene el registro del entrenamiento con la pantalla apagada."
            setShowBadge(false)
        }

        ContextCompat.getSystemService(this, NotificationManager::class.java)
            ?.createNotificationChannel(canal)
    }

    private fun construirNotificacion(): android.app.Notification {
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
            .setContentTitle("ALYRA")
            .setContentText("Entrenamiento en curso")
            .setContentIntent(alTocar)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
