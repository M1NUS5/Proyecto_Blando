package com.example.myapplication1

import android.content.Context
import android.util.Log

/**
 * Punto unico de acceso al modelo de clasificacion de ritmo.
 *
 * TensorFlow Lite reserva memoria nativa que el recolector de Kotlin no libera,
 * asi que el modelo se carga una sola vez en lugar de una por pantalla.
 *
 * Devuelve `null` si el modelo no se puede cargar. Cargarlo implica leer los
 * archivos de assets e inicializar codigo nativo, y cualquiera de esas dos cosas
 * puede fallar en un dispositivo concreto: propagar la excepcion cerraria la
 * aplicacion al abrir la pantalla de IA, mientras que asi el resto sigue
 * funcionando y la pantalla avisa de lo ocurrido.
 */
object PaceClassifierProvider {

    private const val TAG = "PaceClassifier"

    @Volatile
    private var instancia: PaceClassifier? = null

    /** Cierto cuando ya se intento cargar y no se logro; evita reintentar en cada dibujado. */
    @Volatile
    private var cargaFallida = false

    fun obtener(context: Context): PaceClassifier? {
        instancia?.let { return it }
        if (cargaFallida) return null

        return synchronized(this) {
            instancia ?: run {
                runCatching { PaceClassifier(context.applicationContext) }
                    .onSuccess { instancia = it }
                    .onFailure {
                        cargaFallida = true
                        Log.e(TAG, "No se pudo cargar el modelo de ritmo: ${it.message}")
                    }
                    .getOrNull()
            }
        }
    }
}
