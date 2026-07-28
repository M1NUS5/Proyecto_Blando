package com.example.myapplication1

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

private val TealPrimary = Color(0xFF26A69A)
private val TealMedium  = Color(0xFF4DB6AC)
private val TealLight   = Color(0xFF80CBC4)

@Composable
fun SettingsScreen(
    navController: NavController,
    onThemeChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) { AppSettingsStore.cargar(context) }

    val settings by AppSettingsStore.settings.collectAsState()
    val datosReloj by DatosRelojStore.datos.collectAsState()
    val uiColors = appUiColors(settings.temaOscuro)

    val sessionManager = SessionManager(context)
    val userId = sessionManager.getUserId() ?: "No disponible"

    fun actualizarConfiguracion(nuevaConfig: AppSettings) {
        AppSettingsStore.actualizar(context, nuevaConfig)
        enviarConfiguracionAlReloj(context, nuevaConfig)
    }

    fun cerrarSesion() {
        limpiarSesionLocal(context)

        // Sin esto, el nombre y la foto del usuario anterior seguirian en
        // memoria y apareceran en el encabezado de quien inicie sesion despues.
        PerfilStore.limpiar()

        navController.navigate("login") { popUpTo(navController.graph.startDestinationId) { inclusive = true } }
    }

    Box(modifier = Modifier.fillMaxSize().background(uiColors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 14.dp)
                .padding(top = 10.dp, bottom = 24.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = uiColors.textPrimary)
                }
                Text("Configuración", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = uiColors.textPrimary)
            }

            Spacer(modifier = Modifier.height(12.dp))

            SettingsCard(Icons.Default.AccountCircle, "Información de la cuenta", uiColors) {
                SettingText("ID de usuario", userId, uiColors)
                SettingText("Estado de sesión", if (userId != "No disponible") "Sesión activa" else "Sin sesión activa", uiColors)
            }

            SettingsCard(Icons.Default.DirectionsRun, "Preferencias de entrenamiento", uiColors) {
                UnidadPrincipalSelector(settings.unidadPrincipal, uiColors) { nuevaUnidad ->
                    actualizarConfiguracion(settings.copy(unidadPrincipal = nuevaUnidad))
                }
                Spacer(modifier = Modifier.height(8.dp))
                SettingText("Objetivo sugerido", "Mejorar ritmo de carrera", uiColors)
                SettingText("Sincronización", if (datosReloj.timestamp > 0L) "Reloj sincronizado" else "Esperando reloj", uiColors)
            }

            SettingsCard(Icons.Default.AutoAwesome, "Configuración de IA", uiColors) {
                SettingSwitch(
                    title = "Predicción automática",
                    description = if (settings.prediccionIAActiva) "La IA analizará entrenamientos finalizados." else "La IA está apagada en teléfono y reloj.",
                    checked = settings.prediccionIAActiva,
                    uiColors = uiColors,
                    onCheckedChange = { actualizarConfiguracion(settings.copy(prediccionIAActiva = it)) }
                )
                SettingSwitch(
                    title = "Mostrar probabilidades del modelo",
                    description = "Muestra ritmo bajo, óptimo y alto.",
                    checked = settings.mostrarProbabilidades,
                    enabled = settings.prediccionIAActiva,
                    uiColors = uiColors,
                    onCheckedChange = { actualizarConfiguracion(settings.copy(mostrarProbabilidades = it)) }
                )
                SettingSwitch(
                    title = "Guardar última corrida para análisis",
                    description = "La última sesión finalizada se usará para IA.",
                    checked = settings.guardarUltimaCorrida,
                    enabled = settings.prediccionIAActiva,
                    uiColors = uiColors,
                    onCheckedChange = { actualizarConfiguracion(settings.copy(guardarUltimaCorrida = it)) }
                )
            }

            SettingsCard(Icons.Default.Palette, "Apariencia", uiColors) {
                TemaSelector(settings.temaOscuro, uiColors) { oscuro ->
                    actualizarConfiguracion(settings.copy(temaOscuro = oscuro))
                    onThemeChange(oscuro)
                }
            }

            SettingsCard(Icons.Default.Watch, "Sensores y datos en tiempo real", uiColors) {
                SensorStatusRow("GPS / Ubicación", if (datosReloj.estadoEntrenamiento == "CORRIENDO") "Activo durante entrenamiento" else "Disponible", true, uiColors)
                SensorStatusRow("Tiempo de corrida", "${datosReloj.tiempoSegundos}s", datosReloj.tiempoSegundos > 0, uiColors)
                SensorStatusRow("Ritmo / Pace", if (datosReloj.pace > 0f) "%.2f min/km".format(datosReloj.pace) else "Sin datos", datosReloj.pace > 0f, uiColors)
                SensorStatusRow("Frecuencia cardiaca", if (datosReloj.bpm > 0) "${datosReloj.bpm} BPM" else "Esperando BPM", datosReloj.bpm > 0, uiColors)
                SensorStatusRow("Pasos", datosReloj.pasos.toString(), datosReloj.pasos > 0, uiColors)
                SensorStatusRow("Aceleración", "%.2f".format(datosReloj.aceleracion), datosReloj.aceleracion > 0f, uiColors)
                SensorStatusRow("Estado IA recibido", datosReloj.estadoIA, datosReloj.timestamp > 0L, uiColors)
            }

            SettingsCard(Icons.Default.Notifications, "Notificaciones y alertas", uiColors) {
                SettingSwitch(
                    title = "Alertas de ritmo",
                    description = "Avisos cuando el ritmo sea alto o bajo.",
                    checked = settings.alertasRitmo,
                    uiColors = uiColors,
                    onCheckedChange = { actualizarConfiguracion(settings.copy(alertasRitmo = it)) }
                )
            }

            SettingsCard(Icons.Default.Info, "Acerca de la app", uiColors) {
                SettingText("Nombre", "ALYRA - Entrenador Inteligente de Ritmo", uiColors)
                SettingText("Versión", "1.0", uiColors)
                SettingText("Propósito", "Monitorear entrenamientos y analizarlos con IA.", uiColors)
                SettingText("Módulos", "Home, IA, Agenda y Wear OS", uiColors)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { cerrarSesion() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = uiColors.dangerButton, contentColor = Color.White)
            ) { Text("Cerrar sesión", fontWeight = FontWeight.Bold) }

            Spacer(modifier = Modifier.navigationBarsPadding().height(24.dp))
        }
    }
}

@Composable
fun SettingsCard(
    icono: ImageVector,
    title: String,
    uiColors: AppUiColors,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .background(uiColors.card, RoundedCornerShape(20.dp))
            .border(1.dp, uiColors.border, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icono,
                // El titulo que sigue ya describe la seccion, por lo que el icono
                // es decorativo y no debe repetirse en el lector de pantalla.
                contentDescription = null,
                tint = uiColors.primaryButton,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = uiColors.textPrimary)
        }
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
fun SettingText(label: String, value: String, uiColors: AppUiColors) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(text = label, fontSize = 12.sp, color = uiColors.textMuted)
        Text(text = value, fontSize = 14.sp, color = uiColors.textPrimary)
    }
}

@Composable
fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    uiColors: AppUiColors,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (enabled) uiColors.textPrimary else uiColors.textMuted)
            Text(text = description, fontSize = 12.sp, color = uiColors.textMuted)
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = TealPrimary,
                uncheckedThumbColor = uiColors.textMuted,
                uncheckedTrackColor = uiColors.cardSecondary
            )
        )
    }
}

@Composable
fun UnidadPrincipalSelector(unidadActual: String, uiColors: AppUiColors, onUnidadChange: (String) -> Unit) {
    Text("Unidad principal", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = uiColors.textPrimary)
    Spacer(modifier = Modifier.height(6.dp))
    OptionRadioRow("Kilómetros", unidadActual == "kilometros", uiColors) { onUnidadChange("kilometros") }
    OptionRadioRow("Millas", unidadActual == "millas", uiColors) { onUnidadChange("millas") }
}

@Composable
fun TemaSelector(temaOscuro: Boolean, uiColors: AppUiColors, onTemaChange: (Boolean) -> Unit) {
    Text("Tema de la app", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = uiColors.textPrimary)
    Spacer(modifier = Modifier.height(8.dp))
    OptionRadioRow("Claro", !temaOscuro, uiColors) { onTemaChange(false) }
    OptionRadioRow("Oscuro", temaOscuro, uiColors) { onTemaChange(true) }
    Spacer(modifier = Modifier.height(6.dp))
    Text("Tema seleccionado: ${if (temaOscuro) "Oscuro" else "Claro"}", fontSize = 12.sp, color = uiColors.textMuted)
}

@Composable
fun OptionRadioRow(text: String, selected: Boolean, uiColors: AppUiColors, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(uiColors.cardSecondary, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, modifier = Modifier.weight(1f), fontSize = 14.sp, color = uiColors.textPrimary)
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = TealPrimary, unselectedColor = uiColors.textMuted)
        )
    }
}

@Composable
fun SensorStatusRow(label: String, value: String, activo: Boolean, uiColors: AppUiColors) {
    val estadoColor = if (activo) TealPrimary else Color(0xFFFFB74D)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(10.dp).background(estadoColor, CircleShape))
        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = uiColors.textPrimary)
            Text(text = value, fontSize = 12.sp, color = uiColors.textMuted)
        }
    }
}

fun limpiarSesionLocal(context: Context) {
    listOf("session", "app_prefs").forEach { nombre ->
        context.getSharedPreferences(nombre, Context.MODE_PRIVATE).edit().clear().apply()
    }
    SessionManager(context).logout()
}