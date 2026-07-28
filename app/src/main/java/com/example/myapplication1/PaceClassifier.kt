package com.example.myapplication1

import android.content.Context
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PaceInput(
    val pace: Float,
    val heartRate: Float,
    val cadence: Float,
    val acceleration: Float,
    val time: Float
)

data class PaceResult(
    val predictedClass: Int,
    val label: String,
    val confidence: Float,
    val probabilities: FloatArray
)

/**
 * Texto que se muestra al usuario segun la clase predicha por el modelo.
 * Se define una sola vez para que la pantalla de IA y el guardado del
 * entrenamiento entreguen siempre la misma recomendacion.
 */
fun recomendacionParaClase(clase: Int?): String = when (clase) {
    0 -> "Puedes aumentar un poco el ritmo."
    1 -> "Mantén el ritmo actual."
    2 -> "Debes bajar el ritmo."
    else -> "Recomendación no disponible."
}

class PaceClassifier(context: Context) {

    private val interpreter: Interpreter
    private val mean: FloatArray
    private val std: FloatArray

    init {
        interpreter = Interpreter(loadModelFile(context, "pace_model.tflite"))

        val json = loadJsonFromAssets(context, "scaler_config.json")
        val obj = JSONObject(json)

        val meanJson = obj.getJSONArray("mean")
        val stdJson = obj.getJSONArray("std")

        mean = FloatArray(meanJson.length()) { i ->
            meanJson.getDouble(i).toFloat()
        }

        std = FloatArray(stdJson.length()) { i ->
            stdJson.getDouble(i).toFloat()
        }
    }

    fun predict(input: PaceInput): PaceResult {
        val raw = floatArrayOf(
            input.pace,
            input.heartRate,
            input.cadence,
            input.acceleration,
            input.time
        )

        val normalized = FloatArray(raw.size)

        for (i in raw.indices) {
            normalized[i] = (raw[i] - mean[i]) / std[i]
        }

        val inputBuffer = ByteBuffer.allocateDirect(4 * raw.size)
            .order(ByteOrder.nativeOrder())

        normalized.forEach { value ->
            inputBuffer.putFloat(value)
        }

        inputBuffer.rewind()

        val output = Array(1) { FloatArray(3) }

        interpreter.run(inputBuffer, output)

        val probs = output[0]
        val predictedIndex = probs.indices.maxByOrNull { probs[it] } ?: 0
        val confidence = probs[predictedIndex]

        val label = when (predictedIndex) {
            0 -> "ritmo_bajo"
            1 -> "ritmo_optimo"
            2 -> "ritmo_alto"
            else -> "desconocido"
        }

        return PaceResult(
            predictedClass = predictedIndex,
            label = label,
            confidence = confidence,
            probabilities = probs
        )
    }

    fun close() {
        interpreter.close()
    }

    private fun loadModelFile(context: Context, fileName: String): ByteBuffer {
        val bytes = context.assets.open(fileName).readBytes()
        val buffer = ByteBuffer.allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder())

        buffer.put(bytes)
        buffer.rewind()
        return buffer
    }

    private fun loadJsonFromAssets(context: Context, fileName: String): String {
        return context.assets.open(fileName)
            .bufferedReader()
            .use { it.readText() }
    }
}