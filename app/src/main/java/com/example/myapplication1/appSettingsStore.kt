package com.example.myapplication1

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AppSettingsStore {

    private const val PREFS_NAME = "app_settings"

    private const val KEY_PREDICCION_IA = "prediccion_ia_activa"
    private const val KEY_MOSTRAR_PROBABILIDADES = "mostrar_probabilidades"
    private const val KEY_GUARDAR_ULTIMA_CORRIDA = "guardar_ultima_corrida"
    private const val KEY_ALERTAS_RITMO = "alertas_ritmo"
    private const val KEY_TEMA_OSCURO = "tema_oscuro"
    private const val KEY_UNIDAD_PRINCIPAL = "unidad_principal"

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings

    fun cargar(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        _settings.value = AppSettings(
            prediccionIAActiva = prefs.getBoolean(KEY_PREDICCION_IA, true),
            mostrarProbabilidades = prefs.getBoolean(KEY_MOSTRAR_PROBABILIDADES, true),
            guardarUltimaCorrida = prefs.getBoolean(KEY_GUARDAR_ULTIMA_CORRIDA, true),
            alertasRitmo = prefs.getBoolean(KEY_ALERTAS_RITMO, true),
            temaOscuro = prefs.getBoolean(KEY_TEMA_OSCURO, false),
            unidadPrincipal = prefs.getString(KEY_UNIDAD_PRINCIPAL, "kilometros") ?: "kilometros",
            ultimaActualizacion = System.currentTimeMillis()
        )
    }

    fun actualizar(
        context: Context,
        nuevoValor: AppSettings
    ) {
        val config = nuevoValor.copy(
            ultimaActualizacion = System.currentTimeMillis()
        )

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        prefs.edit()
            .putBoolean(KEY_PREDICCION_IA, config.prediccionIAActiva)
            .putBoolean(KEY_MOSTRAR_PROBABILIDADES, config.mostrarProbabilidades)
            .putBoolean(KEY_GUARDAR_ULTIMA_CORRIDA, config.guardarUltimaCorrida)
            .putBoolean(KEY_ALERTAS_RITMO, config.alertasRitmo)
            .putBoolean(KEY_TEMA_OSCURO, config.temaOscuro)
            .putString(KEY_UNIDAD_PRINCIPAL, config.unidadPrincipal)
            .apply()

        _settings.value = config
    }
}