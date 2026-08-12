package com.example.myapplication.presentation

import android.location.Location
import android.os.Build

/**
 * Calcula la distancia recorrida integrando la velocidad que informa el GPS.
 *
 * El receptor obtiene esa velocidad del efecto Doppler, no de la posicion, asi
 * que es mucho mas precisa que sumar la separacion entre lecturas: ese metodo
 * acumula el ruido de cada punto y siempre sobreestima.
 *
 * Cuando no hay velocidad disponible se arrastra la ultima conocida por unos
 * segundos y, si el hueco se alarga, se recurre a la posicion como respaldo.
 */
class MedidorDistancia {

    private companion object {
        /** Precision peor que esta, en metros, hace inutilizable la lectura. */
        const val PRECISION_MAXIMA_M = 18f

        /** Desplazamiento minimo para distinguirse del ruido del receptor. */
        const val MINIMO_METROS = 2.5f

        /** Velocidad maxima creible corriendo (27 km/h). */
        const val MAXIMA_VELOCIDAD_MPS = 7.5f

        /** Por debajo de este valor se considera que la persona esta detenida. */
        const val MINIMA_VELOCIDAD_MPS = 0.3f

        /** Tiempo que se puede arrastrar la ultima velocidad conocida. */
        const val MAXIMO_HUECO_MS = 10_000L

        /** Incertidumbre maxima aceptada en la velocidad informada. */
        const val MAXIMA_INCERTIDUMBRE_VELOCIDAD = 1.5f

        /** Separacion maxima entre lecturas antes de descartar el intervalo. */
        const val MAXIMO_INTERVALO_MS = 30_000L
    }

    private var referencia: Location? = null
    private var lecturaAnteriorMs: Long = 0L

    private var ultimaVelocidad: Float? = null
    private var ultimaVelocidadMs: Long = 0L

    /** Distancia acumulada del entrenamiento, en metros. */
    var metrosAcumulados: Double = 0.0
        private set

    fun reiniciar() {
        referencia = null
        lecturaAnteriorMs = 0L
        ultimaVelocidad = null
        ultimaVelocidadMs = 0L
        metrosAcumulados = 0.0
    }

    /**
     * Incorpora una lectura del GPS y devuelve los metros que agrego.
     *
     * [pasos] confirma que hubo movimiento real: sin pasos, lo que desplaza al
     * punto es la deriva del receptor y no la persona.
     */
    fun procesar(ubicacion: Location, pasos: Int): Double {
        val ahoraMs = ubicacion.time.takeIf { it > 0L } ?: System.currentTimeMillis()

        if (pasos <= 0) {
            lecturaAnteriorMs = ahoraMs
            return 0.0
        }

        // La precision solo se evalua en el respaldo por posicion: la velocidad
        // Doppler es valida aunque el receptor no logre ubicarse con exactitud.
        val precision = if (ubicacion.hasAccuracy()) ubicacion.accuracy else 99f

        val anterior = referencia
        val intervaloMs = if (lecturaAnteriorMs > 0L) ahoraMs - lecturaAnteriorMs else 0L
        lecturaAnteriorMs = ahoraMs

        if (anterior == null) {
            referencia = Location(ubicacion)
            registrarVelocidad(ubicacion, ahoraMs)
            return 0.0
        }

        val velocidad = velocidadUtilizable(ubicacion, ahoraMs)
        registrarVelocidad(ubicacion, ahoraMs)

        // ---- Camino principal: integrar la velocidad en el tiempo ----
        if (velocidad != null && intervaloMs in 1..MAXIMO_INTERVALO_MS) {
            referencia = Location(ubicacion)

            if (velocidad < MINIMA_VELOCIDAD_MPS) return 0.0

            val metros = velocidad.toDouble() * (intervaloMs / 1000.0)
            metrosAcumulados += metros
            return metros
        }

        // ---- Respaldo: diferencia de posiciones con filtro ----
        if (precision > PRECISION_MAXIMA_M) return 0.0

        val metros = anterior.distanceTo(ubicacion)
        val segundos = (ahoraMs - anterior.time).coerceAtLeast(0L) / 1000f

        if (segundos <= 0f) return 0.0

        val velocidadEstimada = metros / segundos

        // Salto imposible: error tipico del receptor entre edificios.
        if (velocidadEstimada > MAXIMA_VELOCIDAD_MPS) return 0.0

        // El desplazamiento debe superar el margen de error para distinguirse
        // del ruido; mientras no lo supere se conserva la referencia.
        val minimoExigido = maxOf(MINIMO_METROS, precision)
        if (metros < minimoExigido) return 0.0

        if (velocidadEstimada < MINIMA_VELOCIDAD_MPS / 2f) {
            // Practicamente detenido: se reancla sin sumar para no acumular deriva.
            referencia = Location(ubicacion)
            return 0.0
        }

        referencia = Location(ubicacion)
        metrosAcumulados += metros.toDouble()
        return metros.toDouble()
    }

    /** Velocidad aprovechable, o `null` si toca usar el respaldo por posicion. */
    private fun velocidadUtilizable(ubicacion: Location, ahoraMs: Long): Float? {
        if (ubicacion.hasSpeed() && velocidadConfiable(ubicacion)) {
            return ubicacion.speed
        }

        val ultima = ultimaVelocidad ?: return null

        return if (ahoraMs - ultimaVelocidadMs <= MAXIMO_HUECO_MS) ultima else null
    }

    private fun velocidadConfiable(ubicacion: Location): Boolean {
        if (ubicacion.speed < 0f || ubicacion.speed > MAXIMA_VELOCIDAD_MPS) return false

        // La incertidumbre de la velocidad solo esta disponible desde Android 8.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ubicacion.hasSpeedAccuracy()) {
            return ubicacion.speedAccuracyMetersPerSecond <= MAXIMA_INCERTIDUMBRE_VELOCIDAD
        }

        return true
    }

    private fun registrarVelocidad(ubicacion: Location, ahoraMs: Long) {
        if (ubicacion.hasSpeed() && velocidadConfiable(ubicacion)) {
            ultimaVelocidad = ubicacion.speed
            ultimaVelocidadMs = ahoraMs
        }
    }
}
