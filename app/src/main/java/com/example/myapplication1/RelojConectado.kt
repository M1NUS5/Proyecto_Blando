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
 * Reloj vinculado al telefono, tal como lo reporta el sistema.
 *
 * [nombre] es el que muestra el propio dispositivo, por ejemplo "Galaxy Watch6".
 * [cerca] indica que la conexion es directa por Bluetooth; cuando es falso el
 * reloj sigue asociado a la cuenta pero se comunica por internet, y los datos
 * del entrenamiento pueden tardar o no llegar.
 */
data class RelojVinculado(
    val nombre: String,
    val cerca: Boolean
)

/**
 * Consulta los relojes vinculados a traves de la capa de datos de Wear OS.
 *
 * Conviene aclarar por que no se usa Bluetooth directamente: ALYRA se comunica
 * con el reloj mediante la Wearable Data Layer de Google Play Services, que se
 * apoya en el emparejamiento que ya hizo el sistema desde la aplicacion
 * complementaria (Galaxy Wearable o Wear OS). Listar dispositivos Bluetooth y
 * dejar elegir uno no serviria: la aplicacion no puede emparejar un reloj por su
 * cuenta, y el que se eligiera ahi no quedaria vinculado a ALYRA.
 *
 * Lo que si aporta valor, y es lo que hace esta funcion, es mostrar el nombre
 * real del reloj que el sistema ya reconoce.
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
 * Abre los ajustes de Bluetooth del sistema.
 *
 * Sirve para restablecer la conexion cuando el reloj ya esta emparejado pero se
 * desconecto: sin enlace Bluetooth la capa de datos deja de entregar lo que
 * envia el reloj. No sirve para vincular un reloj nuevo, eso se hace desde la
 * aplicacion complementaria del fabricante.
 */
fun abrirAjustesBluetooth(context: Context) {
    val intento = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { context.startActivity(intento) }
        .onFailure { Log.d(TAG, "No se pudo abrir Bluetooth: ${it.message}") }
}

/**
 * Abre la aplicacion complementaria donde de verdad se emparejan los relojes.
 *
 * Se intenta primero Galaxy Wearable, que es la que corresponde a los relojes
 * Samsung, y si no esta instalada se recurre a la aplicacion de Wear OS. Cuando
 * no hay ninguna, se devuelve false para que la pantalla ofrezca otra salida en
 * lugar de fallar en silencio.
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
