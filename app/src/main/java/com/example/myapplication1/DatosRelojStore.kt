package com.example.myapplication1

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object DatosRelojStore {

    private val _datos = MutableStateFlow(DatosReloj())
    val datos: StateFlow<DatosReloj> = _datos

    fun actualizar(nuevosDatos: DatosReloj) {
        _datos.value = nuevosDatos
    }
}