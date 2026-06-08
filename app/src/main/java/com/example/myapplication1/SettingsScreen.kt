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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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

@Composable
fun SettingsScreen(
    navController: NavController,
    onThemeChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        AppSettingsStore.cargar(context)
    }

    val settings by AppSettingsStore.settings.collectAsState()
    val datosReloj by DatosRelojStore.datos.collectAsState()

    val sessionManager = SessionManager(context)
    val userId = sessionManager.getUserId() ?: "No disponible"

    fun actualizarConfiguracion(nuevaConfig: AppSettings) {
        AppSettingsStore.actualizar(context, nuevaConfig)
        enviarConfiguracionAlReloj(context, nuevaConfig)
    }

    fun cerrarSesion() {
        limpiarSesionLocal(context)

        navController.navigate("login") {
            popUpTo(navController.graph.startDestinationId) {
                inclusive = true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (settings.temaOscuro) Color(0xFF101010) else Color(0xFFF2F2F2)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(top = 10.dp, bottom = 24.dp)
        ) {
            HeaderConfiguracion(
                titulo = "Configuración",
                temaOscuro = settings.temaOscuro,
                onBack = {
                    navController.popBackStack()
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingsCard(
                title = "Información de la cuenta",
                emoji = "👤",
                temaOscuro = settings.temaOscuro
            ) {
                SettingText(
                    label = "ID de usuario",
                    value = userId,
                    temaOscuro = settings.temaOscuro
                )

                SettingText(
                    label = "Estado de sesión",
                    value = if (userId != "No disponible") "Sesión activa" else "Sin sesión activa",
                    temaOscuro = settings.temaOscuro
                )
            }

            SettingsCard(
                title = "Preferencias de entrenamiento",
                emoji = "🏃",
                temaOscuro = settings.temaOscuro
            ) {
                UnidadPrincipalSelector(
                    unidadActual = settings.unidadPrincipal,
                    temaOscuro = settings.temaOscuro,
                    onUnidadChange = { nuevaUnidad ->
                        actualizarConfiguracion(
                            settings.copy(
                                unidadPrincipal = nuevaUnidad
                            )
                        )
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingText(
                    label = "Objetivo sugerido",
                    value = "Mejorar ritmo de carrera",
                    temaOscuro = settings.temaOscuro
                )

                SettingText(
                    label = "Sincronización",
                    value = if (datosReloj.timestamp > 0L) {
                        "Reloj sincronizado"
                    } else {
                        "Esperando reloj"
                    },
                    temaOscuro = settings.temaOscuro
                )
            }

            SettingsCard(
                title = "Configuración de IA",
                emoji = "🤖",
                temaOscuro = settings.temaOscuro
            ) {
                SettingSwitch(
                    title = "Predicción automática",
                    description = if (settings.prediccionIAActiva) {
                        "La IA analizará entrenamientos finalizados."
                    } else {
                        "La IA está apagada en teléfono y reloj."
                    },
                    checked = settings.prediccionIAActiva,
                    temaOscuro = settings.temaOscuro,
                    onCheckedChange = { activo ->
                        actualizarConfiguracion(
                            settings.copy(
                                prediccionIAActiva = activo
                            )
                        )
                    }
                )

                SettingSwitch(
                    title = "Mostrar probabilidades del modelo",
                    description = "Muestra ritmo bajo, óptimo y alto.",
                    checked = settings.mostrarProbabilidades,
                    enabled = settings.prediccionIAActiva,
                    temaOscuro = settings.temaOscuro,
                    onCheckedChange = { activo ->
                        actualizarConfiguracion(
                            settings.copy(
                                mostrarProbabilidades = activo
                            )
                        )
                    }
                )

                SettingSwitch(
                    title = "Guardar última corrida para análisis",
                    description = "La última sesión finalizada se usará para IA.",
                    checked = settings.guardarUltimaCorrida,
                    enabled = settings.prediccionIAActiva,
                    temaOscuro = settings.temaOscuro,
                    onCheckedChange = { activo ->
                        actualizarConfiguracion(
                            settings.copy(
                                guardarUltimaCorrida = activo
                            )
                        )
                    }
                )
            }

            SettingsCard(
                title = "Apariencia",
                emoji = "🎨",
                temaOscuro = settings.temaOscuro
            ) {
                TemaSelector(
                    temaOscuro = settings.temaOscuro,
                    onTemaChange = { oscuro ->
                        val nuevaConfig = settings.copy(
                            temaOscuro = oscuro
                        )

                        actualizarConfiguracion(nuevaConfig)
                        onThemeChange(oscuro)
                    }
                )
            }

            SettingsCard(
                title = "Sensores y datos en tiempo real",
                emoji = "⌚",
                temaOscuro = settings.temaOscuro
            ) {
                SensorStatusRow(
                    label = "GPS / Ubicación",
                    value = if (datosReloj.estadoEntrenamiento == "CORRIENDO") {
                        "Activo durante entrenamiento"
                    } else {
                        "Disponible"
                    },
                    activo = true,
                    temaOscuro = settings.temaOscuro
                )

                SensorStatusRow(
                    label = "Tiempo de corrida",
                    value = "${datosReloj.tiempoSegundos}s",
                    activo = datosReloj.tiempoSegundos > 0,
                    temaOscuro = settings.temaOscuro
                )

                SensorStatusRow(
                    label = "Ritmo / Pace",
                    value = if (datosReloj.pace > 0f) {
                        "%.2f min/km".format(datosReloj.pace)
                    } else {
                        "Sin datos"
                    },
                    activo = datosReloj.pace > 0f,
                    temaOscuro = settings.temaOscuro
                )

                SensorStatusRow(
                    label = "Frecuencia cardiaca",
                    value = if (datosReloj.bpm > 0) {
                        "${datosReloj.bpm} BPM"
                    } else {
                        "Esperando BPM"
                    },
                    activo = datosReloj.bpm > 0,
                    temaOscuro = settings.temaOscuro
                )

                SensorStatusRow(
                    label = "Pasos",
                    value = datosReloj.pasos.toString(),
                    activo = datosReloj.pasos > 0,
                    temaOscuro = settings.temaOscuro
                )

                SensorStatusRow(
                    label = "Aceleración",
                    value = "%.2f".format(datosReloj.aceleracion),
                    activo = datosReloj.aceleracion > 0f,
                    temaOscuro = settings.temaOscuro
                )

                SensorStatusRow(
                    label = "Estado IA recibido",
                    value = datosReloj.estadoIA,
                    activo = datosReloj.timestamp > 0L,
                    temaOscuro = settings.temaOscuro
                )
            }

            SettingsCard(
                title = "Notificaciones y alertas",
                emoji = "🔔",
                temaOscuro = settings.temaOscuro
            ) {
                SettingSwitch(
                    title = "Alertas de ritmo",
                    description = "Avisos cuando el ritmo sea alto o bajo.",
                    checked = settings.alertasRitmo,
                    temaOscuro = settings.temaOscuro,
                    onCheckedChange = { activo ->
                        actualizarConfiguracion(
                            settings.copy(
                                alertasRitmo = activo
                            )
                        )
                    }
                )
            }

            SettingsCard(
                title = "Acerca de la app",
                emoji = "ℹ️",
                temaOscuro = settings.temaOscuro
            ) {
                SettingText(
                    label = "Nombre",
                    value = "Entrenador Inteligente de Ritmo",
                    temaOscuro = settings.temaOscuro
                )

                SettingText(
                    label = "Versión",
                    value = "1.0",
                    temaOscuro = settings.temaOscuro
                )

                SettingText(
                    label = "Propósito",
                    value = "Monitorear entrenamientos y analizarlos con IA.",
                    temaOscuro = settings.temaOscuro
                )

                SettingText(
                    label = "Módulos",
                    value = "Home, IA, Agenda y Wear OS",
                    temaOscuro = settings.temaOscuro
                )
            }

            Button(
                onClick = {
                    cerrarSesion()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2F6FA3),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Cerrar sesión",
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun HeaderConfiguracion(
    titulo: String,
    temaOscuro: Boolean,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onBack
        ) {
            Text(
                text = "←",
                fontSize = 24.sp,
                color = if (temaOscuro) Color.White else Color.Black
            )
        }

        Text(
            text = titulo,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = if (temaOscuro) Color.White else Color.Black
        )
    }
}

@Composable
fun SettingsCard(
    title: String,
    emoji: String,
    temaOscuro: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardColor = if (temaOscuro) Color(0xFF1B1B1B) else Color.White
    val textColor = if (temaOscuro) Color.White else Color(0xFF202020)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .background(
                color = cardColor,
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = 1.dp,
                color = if (temaOscuro) Color(0xFF333333) else Color(0xFFE5E5E5),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = emoji,
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.padding(horizontal = 4.dp))

            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        content()
    }
}

@Composable
fun SettingText(
    label: String,
    value: String,
    temaOscuro: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (temaOscuro) Color(0xFFBDBDBD) else Color.Gray
        )

        Text(
            text = value,
            fontSize = 14.sp,
            color = if (temaOscuro) Color.White else Color(0xFF303030)
        )
    }
}

@Composable
fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    temaOscuro: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val textColor = if (temaOscuro) Color.White else Color(0xFF202020)
    val subTextColor = if (temaOscuro) Color(0xFFBDBDBD) else Color.Gray

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) textColor else subTextColor
            )

            Text(
                text = description,
                fontSize = 12.sp,
                color = subTextColor
            )
        }

        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun UnidadPrincipalSelector(
    unidadActual: String,
    temaOscuro: Boolean,
    onUnidadChange: (String) -> Unit
) {
    val textColor = if (temaOscuro) Color.White else Color(0xFF202020)

    Text(
        text = "Unidad principal",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = textColor
    )

    Spacer(modifier = Modifier.height(6.dp))

    OptionRadioRow(
        text = "Kilómetros",
        selected = unidadActual == "kilometros",
        temaOscuro = temaOscuro,
        onClick = {
            onUnidadChange("kilometros")
        }
    )

    OptionRadioRow(
        text = "Millas",
        selected = unidadActual == "millas",
        temaOscuro = temaOscuro,
        onClick = {
            onUnidadChange("millas")
        }
    )
}

@Composable
fun TemaSelector(
    temaOscuro: Boolean,
    onTemaChange: (Boolean) -> Unit
) {
    Text(
        text = "Tema de la app",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = if (temaOscuro) Color.White else Color(0xFF202020)
    )

    Spacer(modifier = Modifier.height(8.dp))

    OptionRadioRow(
        text = "Claro",
        selected = !temaOscuro,
        temaOscuro = temaOscuro,
        onClick = {
            onTemaChange(false)
        }
    )

    OptionRadioRow(
        text = "Oscuro",
        selected = temaOscuro,
        temaOscuro = temaOscuro,
        onClick = {
            onTemaChange(true)
        }
    )

    Spacer(modifier = Modifier.height(6.dp))

    Text(
        text = "Tema seleccionado: ${if (temaOscuro) "Oscuro" else "Claro"}",
        fontSize = 12.sp,
        color = if (temaOscuro) Color(0xFFBDBDBD) else Color.Gray
    )
}

@Composable
fun OptionRadioRow(
    text: String,
    selected: Boolean,
    temaOscuro: Boolean,
    onClick: () -> Unit
) {
    val fondo = if (temaOscuro) Color(0xFF242424) else Color(0xFFF7F7F7)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                color = fondo,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            color = if (temaOscuro) Color.White else Color(0xFF303030)
        )

        RadioButton(
            selected = selected,
            onClick = onClick
        )
    }
}

@Composable
fun SensorStatusRow(
    label: String,
    value: String,
    activo: Boolean,
    temaOscuro: Boolean
) {
    val estadoColor = if (activo) Color(0xFF2ECC71) else Color(0xFFFFB74D)
    val textColor = if (temaOscuro) Color.White else Color(0xFF303030)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = estadoColor,
                    shape = RoundedCornerShape(50.dp)
                )
                .padding(horizontal = 5.dp, vertical = 5.dp)
        )

        Spacer(modifier = Modifier.padding(horizontal = 4.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )

            Text(
                text = value,
                fontSize = 12.sp,
                color = if (temaOscuro) Color(0xFFBDBDBD) else Color.Gray
            )
        }
    }
}

fun limpiarSesionLocal(context: Context) {
    val posiblesPrefs = listOf(
        "session",
        "user_session",
        "UserPrefs",
        "user_prefs",
        "app_prefs"
    )

    posiblesPrefs.forEach { nombre ->
        context.getSharedPreferences(nombre, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}