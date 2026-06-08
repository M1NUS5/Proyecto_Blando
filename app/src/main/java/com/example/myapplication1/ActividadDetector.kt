package com.example.myapplication1

data class ActividadDetectada(
    val estado: String,
    val consejo: String,
    val intensidad: Int
)

fun detectarActividadPorSensores(
    bpm: Int,
    pasos: Int,
    tiempoSegundos: Long,
    aceleracion: Float,
    pace: Double
): ActividadDetectada {

    val minutos = tiempoSegundos / 60.0

    val cadencia = if (minutos > 0.0 && pasos > 0) {
        pasos / minutos
    } else {
        0.0
    }

    if (tiempoSegundos < 8) {
        return ActividadDetectada(
            estado = "Preparando sensores",
            consejo = "Espera unos segundos para estabilizar los datos.",
            intensidad = 0
        )
    }

    if (pasos == 0 && cadencia == 0.0 && aceleracion < 0.45f) {
        return ActividadDetectada(
            estado = "Reposo",
            consejo = "Estás en reposo. No se detecta movimiento real.",
            intensidad = 0
        )
    }

    if (pasos == 0 && bpm in 45..110 && aceleracion < 0.70f) {
        return ActividadDetectada(
            estado = "Reposo",
            consejo = "Frecuencia cardiaca normal y sin pasos detectados.",
            intensidad = 0
        )
    }

    if (cadencia in 1.0..85.0 && bpm < 125) {
        return ActividadDetectada(
            estado = "Caminando",
            consejo = "Movimiento ligero detectado. Ritmo estable de caminata.",
            intensidad = 1
        )
    }

    if (cadencia in 86.0..135.0 || bpm in 125..150) {
        return ActividadDetectada(
            estado = "Trotando",
            consejo = "Ritmo moderado detectado. Mantén la respiración controlada.",
            intensidad = 2
        )
    }

    if (cadencia > 135.0 || bpm > 150) {
        return ActividadDetectada(
            estado = "Corriendo",
            consejo = "Ritmo alto detectado. Controla tu frecuencia cardiaca.",
            intensidad = 3
        )
    }

    if (pace > 0.0 && pace <= 6.5 && bpm >= 135) {
        return ActividadDetectada(
            estado = "Corriendo",
            consejo = "Pace rápido y BPM elevado. Estás corriendo.",
            intensidad = 3
        )
    }

    if (pace > 6.5 && pace <= 10.0 && bpm >= 115) {
        return ActividadDetectada(
            estado = "Trotando",
            consejo = "Pace moderado. Se detecta trote.",
            intensidad = 2
        )
    }

    return ActividadDetectada(
        estado = "Caminando",
        consejo = "Movimiento detectado con intensidad baja.",
        intensidad = 1
    )
}