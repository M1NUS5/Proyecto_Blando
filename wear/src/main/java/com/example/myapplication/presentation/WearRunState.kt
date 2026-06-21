package com.example.myapplication.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object WearRunState {
    private val _accion = MutableStateFlow<String?>(null)
    val accion: StateFlow<String?> = _accion

    fun iniciar()   { _accion.value = ACCION_INICIAR }
    fun pausar()    { _accion.value = ACCION_PAUSAR }
    fun reanudar()  { _accion.value = ACCION_REANUDAR }
    fun finalizar() { _accion.value = ACCION_FINALIZAR }
    fun consumir()  { _accion.value = null }
}