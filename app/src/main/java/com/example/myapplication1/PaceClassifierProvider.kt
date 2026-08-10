package com.example.myapplication1

import android.content.Context

/**
 * Punto unico de acceso al modelo de clasificacion de ritmo.
 *
 * TensorFlow Lite reserva memoria nativa que el recolector de Kotlin no libera,
 * asi que el modelo se carga una sola vez en lugar de una por pantalla.
 */
object PaceClassifierProvider {

    @Volatile
    private var instancia: PaceClassifier? = null

    fun obtener(context: Context): PaceClassifier {
        return instancia ?: synchronized(this) {
            instancia ?: PaceClassifier(context.applicationContext).also { instancia = it }
        }
    }
}
