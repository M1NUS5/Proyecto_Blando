package com.example.myapplication1

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Analisis de comida en curso, conservado fuera de la pantalla que lo muestra.
 *
 * La barra inferior navega con `popUpTo(RUTA_INICIO)`, que saca la pantalla
 * actual del historial: al hacerlo, Android la destruye junto con todo el estado
 * declarado con `remember`. El efecto para el usuario era que, si analizaba una
 * fotografia y cambiaba de pestana antes de guardarla, al volver encontraba la
 * pantalla vacia y tenia que tomar la foto y analizarla otra vez, gastando ademas
 * una peticion mas al servicio de IA.
 *
 * Guardar el estado aqui lo desliga por completo del ciclo de vida de la
 * pantalla. Como efecto secundario deseable, un analisis que se lanzo y quedo
 * en camino termina de llegar aunque el usuario se haya cambiado de pestana, y
 * lo encuentra listo al regresar.
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
     * Alcance para el trabajo que no debe morir con la pantalla.
     *
     * `rememberCoroutineScope()` se cancela en cuanto la pantalla sale de
     * composicion, de modo que un reintento que estuviera esperando su turno
     * nunca llegaria a ejecutarse y el indicador de "Analizando..." se quedaria
     * encendido de forma permanente al regresar. Este alcance vive lo que vive
     * la aplicacion, asi que la preparacion de la imagen y los reintentos
     * terminan pase lo que pase.
     */
    val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Modifica el estado a partir del actual, al estilo de `copy()`. */
    fun actualizar(bloque: (AnalisisComida) -> AnalisisComida) {
        _estado.value = bloque(_estado.value)
    }

    /**
     * Vacia el analisis. Se llama al cerrar sesion, para no mostrarle a otra
     * persona la fotografia y las cifras de quien uso la aplicacion antes.
     *
     * No se llama al guardar la comida: despues de guardar conviene que el
     * resultado siga en pantalla para que el usuario alcance a leerlo. Se
     * reemplaza solo cuando elige otra fotografia.
     */
    fun limpiar() {
        _estado.value = AnalisisComida()
    }
}
