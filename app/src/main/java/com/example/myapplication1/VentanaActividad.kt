package com.example.myapplication1

/**
 * Guarda la actividad de los ultimos segundos para poder distinguir lo que la
 * persona esta haciendo *ahora* de lo que hizo durante todo el entrenamiento.
 *
 * ### El problema que resuelve
 *
 * La deteccion de actividad usaba el contador acumulado de pasos de la sesion.
 * Con eso, la condicion de reposo era `pasos == 0`, que solo se cumple antes de
 * dar el primer paso: quien caminaba un rato y despues se sentaba seguia
 * apareciendo como "Caminando" indefinidamente, porque ese contador jamas
 * regresa a cero. La cadencia acumulada tampoco servia, ya que al promediarse
 * sobre toda la sesion tarda minutos en reflejar que la persona se detuvo.
 *
 * Comparando el contador de hace unos segundos con el actual, en cambio, un
 * periodo sin pasos se nota de inmediato.
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
     * Pasos por minuto de los ultimos segundos, o `null` mientras no haya
     * historia suficiente. Devolver null en vez de cero es deliberado: recien
     * iniciado el entrenamiento, un cero seria indistinguible de estar quieto y
     * haria que la pantalla anunciara reposo antes de tener con que afirmarlo.
     */
    fun cadenciaReciente(): Double? {
        val lapso = lapsoMs()
        if (lapso < minimoUtilMs) return null

        val minutos = lapso / 60_000.0
        if (minutos <= 0.0) return null

        return pasosRecientes() / minutos
    }
}
