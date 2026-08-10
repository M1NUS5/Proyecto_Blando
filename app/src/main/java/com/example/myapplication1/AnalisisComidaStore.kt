package com.example.myapplication1

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Analisis de comida en curso, conservado fuera de la pantalla que lo muestra.
 *
 * La barra inferior saca la pantalla del historial al cambiar de pestana y
 * Android la destruye con todo su estado. Guardarlo aqui hace que el resultado
 * sobreviva al cambio de pestana, incluso si el analisis aun venia en camino.
 */
data class AnalisisComida(
    val imagenUri: String? = null,
    val imagenBase64: String? = null,
    val tipoImagen: String = "image/jpeg",
    val resultado: FoodAnalysisResponse? = null,
    val error: String? = null,

    val preparandoImagen: Boolean = false,
    val analizando: Boolean = false,
    val reintentando: Boolean = false,
    val guardando: Boolean = false,
    val guardadoOk: Boolean = false
)

object AnalisisComidaStore {

    private val _estado = MutableStateFlow(AnalisisComida())
    val estado: StateFlow<AnalisisComida> = _estado

    /**
     * Alcance de vida larga para los reintentos y la preparacion de la imagen.
     * `rememberCoroutineScope()` no sirve aqui: se cancela al salir la pantalla
     * y dejaria el indicador de "Analizando..." encendido para siempre.
     */
    val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Modifica el estado a partir del actual, al estilo de `copy()`. */
    fun actualizar(bloque: (AnalisisComida) -> AnalisisComida) {
        _estado.value = bloque(_estado.value)
    }

    /** Vacia el analisis al cerrar sesion, no al guardar la comida. */
    fun limpiar() {
        _estado.value = AnalisisComida()
    }
}
