package com.example.myapplication1

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.wearable.Wearable

private const val TAG = "RelojConectado"

/**
 * Reloj vinculado al telefono. [nombre] es el que muestra el dispositivo, por
 * ejemplo "Galaxy Watch6"; [cerca] indica conexion directa por Bluetooth.
 */
data class RelojVinculado(
    val nombre: String,
    val cerca: Boolean
)

/**
 * Consulta los relojes vinculados por la capa de datos de Wear OS.
 *
 * No se usa Bluetooth directamente porque la aplicacion no puede emparejar un
 * reloj: eso lo hace el sistema desde Galaxy Wearable o Wear OS.
 */
@Composable
fun rememberRelojesVinculados(refrescos: Int = 0): State<List<RelojVinculado>> {
    val context = LocalContext.current
    val estado = remember { mutableStateOf(emptyList<RelojVinculado>()) }

    LaunchedEffect(refrescos) {
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodos ->
                estado.value = nodos.map { nodo ->
                    RelojVinculado(
                        nombre = nodo.displayName.ifBlank { "Reloj sin nombre" },
                        cerca = nodo.isNearby
                    )
                }
            }
            .addOnFailureListener { error ->
                Log.d(TAG, "No se pudieron consultar los relojes: ${error.message}")
                estado.value = emptyList()
            }
    }

    return estado
}

/**
 * Abre los ajustes de Bluetooth. Sirve para reconectar un reloj ya emparejado,
 * no para vincular uno nuevo.
 */
fun abrirAjustesBluetooth(context: Context) {
    val intento = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { context.startActivity(intento) }
        .onFailure { Log.d(TAG, "No se pudo abrir Bluetooth: ${it.message}") }
}

/**
 * Abre la aplicacion donde se emparejan los relojes: primero Galaxy Wearable y,
 * si no esta, Wear OS. Devuelve false cuando no hay ninguna instalada.
 */
fun abrirAppDelReloj(context: Context): Boolean {
    val paquetes = listOf(
        "com.samsung.android.app.watchmanager", // Galaxy Wearable
        "com.google.android.wearable.app"       // Wear OS
    )

    paquetes.forEach { paquete ->
        val intento = context.packageManager.getLaunchIntentForPackage(paquete)
        if (intento != null) {
            intento.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val abrio = runCatching { context.startActivity(intento) }.isSuccess
            if (abrio) return true
        }
    }

    return false
}
