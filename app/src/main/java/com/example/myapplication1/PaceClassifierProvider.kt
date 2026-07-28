package com.example.myapplication1

import android.content.Context

/**
 * Punto unico de acceso al modelo de clasificacion de ritmo.
 *
 * Cargar el modelo implica leer `pace_model.tflite` de assets y reservar memoria
 * nativa para el interprete de TensorFlow Lite. Esa memoria no la libera el
 * recolector de basura de Kotlin, por lo que crear una instancia nueva en cada
 * pantalla dejaria interpretes sin liberar cada vez que el usuario navega.
 *
 * Con esta clase el modelo se carga una sola vez y se reutiliza durante toda la
 * vida de la aplicacion: se evita la fuga y ademas la pantalla de IA abre mas
 * rapido, porque ya no vuelve a leer el modelo desde el disco.
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
