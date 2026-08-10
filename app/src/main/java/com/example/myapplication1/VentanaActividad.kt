package com.example.myapplication1

/**
 * Guarda los pasos de los ultimos segundos para distinguir lo que la persona
 * hace *ahora* de lo que hizo en todo el entrenamiento.
 *
 * El contador acumulado no sirve para detectar que alguien se detuvo: nunca
 * vuelve a cero. Comparar el valor de hace unos segundos con el actual si.
 */
class VentanaActividad(
    /** Cuanto tiempo hacia atras se observa. */
    private val ventanaMs: Long = 25_000L,
    /** Minimo de historia necesaria para que la medicion signifique algo. */
    private val minimoUtilMs: Long = 10_000L
) {

    private val muestras = ArrayDeque<Pair<Long, Int>>()

    /** Añade el contador acumulado de pasos leido en este instante. */
    fun registrar(pasosAcumulados: Int, ahoraMs: Long = System.currentTimeMillis()) {
        muestras.addLast(ahoraMs to pasosAcumulados)

        // Se conserva siempre una muestra anterior al inicio de la ventana para
        // que la diferencia cubra el periodo completo y no uno mas corto.
        while (muestras.size > 2 && ahoraMs - muestras.first().first > ventanaMs) {
            muestras.removeFirst()
        }
    }

    fun reiniciar() {
        muestras.clear()
    }

    private fun lapsoMs(): Long {
        if (muestras.size < 2) return 0L
        return muestras.last().first - muestras.first().first
    }

    /** Pasos dados dentro de la ventana observada. */
    fun pasosRecientes(): Int {
        if (muestras.size < 2) return 0
        return (muestras.last().second - muestras.first().second).coerceAtLeast(0)
    }

    /**
     * Pasos por minuto de los ultimos segundos, o `null` si aun no hay historia
     * suficiente. Devuelve null y no cero para no anunciar reposo al arrancar.
     */
    fun cadenciaReciente(): Double? {
        val lapso = lapsoMs()
        if (lapso < minimoUtilMs) return null

        val minutos = lapso / 60_000.0
        if (minutos <= 0.0) return null

        return pasosRecientes() / minutos
    }
}
