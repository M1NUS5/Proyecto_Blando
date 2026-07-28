package com.example.myapplication1

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.RoundCap
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import java.util.Locale

@Composable
fun IAScreen(navController: NavController) {
    val context = LocalContext.current
    val classifier = remember { PaceClassifierProvider.obtener(context) }

    val settings by AppSettingsStore.settings.collectAsState()
    val uiColors = appUiColors(settings.temaOscuro)

    var pace by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var distance by remember { mutableStateOf("") }

    var heartRate by remember { mutableStateOf("") }
    var cadence by remember { mutableStateOf("") }
    var acceleration by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("") }

    var clase by remember { mutableStateOf("") }
    var etiqueta by remember { mutableStateOf("") }
    var confianza by remember { mutableStateOf("") }
    var probBajo by remember { mutableStateOf("") }
    var probOptimo by remember { mutableStateOf("") }
    var probAlto by remember { mutableStateOf("") }
    var recomendacion by remember { mutableStateOf("") }

    var resultadoTipo by remember { mutableIntStateOf(-1) }

    var mensaje by remember {
        mutableStateOf("Finaliza un entrenamiento en Home para analizarlo automáticamente")
    }

    fun limpiarPrediccion(mensajeNuevo: String) {
        mensaje = mensajeNuevo
        clase = ""
        etiqueta = ""
        confianza = ""
        probBajo = ""
        probOptimo = ""
        probAlto = ""
        recomendacion = ""
        resultadoTipo = -1
    }

    fun ejecutarPrediccionConDatos(
        paceValor: String,
        timeValor: String,
        heartRateValor: String,
        cadenceValor: String,
        accelerationValor: String
    ) {
        if (!settings.prediccionIAActiva) {
            limpiarPrediccion("La predicción automática está desactivada desde Configuración")
            return
        }

        // Se nombra el dato ausente en lugar de un aviso generico: sin esa
        // precision el usuario no sabe si el problema fue el GPS, el reloj o la
        // duracion del entrenamiento, ni que hacer para obtener una prediccion.
        val faltantes = buildList {
            if (paceValor.isBlank()) add("el ritmo")
            if (timeValor.isBlank()) add("el tiempo")
            if (heartRateValor.isBlank()) add("la frecuencia cardiaca")
            if (cadenceValor.isBlank()) add("la cadencia")
            if (accelerationValor.isBlank()) add("la aceleración")
        }

        if (faltantes.isNotEmpty()) {
            val listado = when (faltantes.size) {
                1 -> faltantes.first()
                else -> faltantes.dropLast(1).joinToString(", ") + " y " + faltantes.last()
            }

            val ayuda = if (paceValor.isBlank()) {
                " El ritmo necesita al menos 15 segundos y 30 metros de recorrido con buena señal de GPS."
            } else {
                " Revisa que el reloj esté conectado y colocado correctamente."
            }

            limpiarPrediccion("No se puede analizar: falta $listado.$ayuda")
            return
        }

        val input = PaceInput(
            pace = paceValor.toFloatOrNull() ?: 0f,
            heartRate = heartRateValor.toFloatOrNull() ?: 0f,
            cadence = cadenceValor.toFloatOrNull() ?: 0f,
            acceleration = accelerationValor.toFloatOrNull() ?: 0f,
            time = timeValor.toFloatOrNull() ?: 0f
        )

        val prediccion = classifier.predict(input)

        clase = prediccion.predictedClass.toString()
        etiqueta = prediccion.label
        confianza = String.format(Locale.US, "%.2f%%", prediccion.confidence * 100f)
        probBajo = String.format(Locale.US, "%.2f%%", prediccion.probabilities[0] * 100f)
        probOptimo = String.format(Locale.US, "%.2f%%", prediccion.probabilities[1] * 100f)
        probAlto = String.format(Locale.US, "%.2f%%", prediccion.probabilities[2] * 100f)

        resultadoTipo = prediccion.predictedClass

        recomendacion = recomendacionParaClase(prediccion.predictedClass)

        mensaje = "Predicción realizada con los datos reales del entrenamiento finalizado"
    }

    fun cargarDatosUltimaCorrida() {
        if (!settings.prediccionIAActiva) {
            limpiarPrediccion("La IA está desactivada. Actívala en Configuración para analizar entrenamientos.")
            return
        }

        if (!RunDataStore.hasFinishedRun) {
            limpiarPrediccion("No hay entrenamiento finalizado para analizar")
            return
        }

        val paceReal = if (RunDataStore.lastPace > 0f) {
            String.format(Locale.US, "%.2f", RunDataStore.lastPace)
        } else {
            ""
        }

        val timeReal = if (RunDataStore.lastTime > 0f) {
            String.format(Locale.US, "%.0f", RunDataStore.lastTime)
        } else {
            ""
        }

        val distanceReal = if (RunDataStore.lastDistance > 0f) {
            String.format(Locale.US, "%.2f", RunDataStore.lastDistance)
        } else {
            ""
        }

        val bpmReal = if (RunDataStore.lastBpm > 0f) {
            String.format(Locale.US, "%.0f", RunDataStore.lastBpm)
        } else {
            ""
        }

        val cadenceReal = if (RunDataStore.lastCadence > 0f) {
            String.format(Locale.US, "%.0f", RunDataStore.lastCadence)
        } else {
            ""
        }

        val accelerationReal = if (RunDataStore.lastAcceleration > 0f) {
            String.format(Locale.US, "%.2f", RunDataStore.lastAcceleration)
        } else {
            ""
        }

        val stepsReal = if (RunDataStore.lastSteps > 0) {
            RunDataStore.lastSteps.toString()
        } else {
            ""
        }

        pace = paceReal
        time = timeReal
        distance = distanceReal
        heartRate = bpmReal
        cadence = cadenceReal
        acceleration = accelerationReal
        steps = stepsReal

        ejecutarPrediccionConDatos(
            paceValor = paceReal,
            timeValor = timeReal,
            heartRateValor = bpmReal,
            cadenceValor = cadenceReal,
            accelerationValor = accelerationReal
        )
    }

    fun ejecutarPrediccionManual() {
        ejecutarPrediccionConDatos(
            paceValor = pace,
            timeValor = time,
            heartRateValor = heartRate,
            cadenceValor = cadence,
            accelerationValor = acceleration
        )
    }

    LaunchedEffect(
        settings.prediccionIAActiva,
        settings.mostrarProbabilidades,
        settings.guardarUltimaCorrida
    ) {
        cargarDatosUltimaCorrida()
    }

    val hayRecorrido = RunDataStore.hasFinishedRun && RunDataStore.finishedPathPoints.size >= 2
    val puntosRecorrido = if (hayRecorrido) RunDataStore.finishedPathPoints else emptyList()

    val marcasKilometro = remember(puntosRecorrido) {
        RecorridoMapaUtils.marcasDeKilometro(puntosRecorrido)
    }

    val camaraRecorrido = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(31.6904, -106.4245), 15f)
    }

    // El encuadre se calcula a partir de los limites reales del trazo en vez de
    // estimar un nivel de acercamiento por la diferencia de coordenadas: asi el
    // recorrido siempre se ve completo y centrado, sin quedar cortado ni
    // demasiado lejano. El margen deja aire alrededor de la linea.
    LaunchedEffect(puntosRecorrido) {
        val limites = RecorridoMapaUtils.limitesDelRecorrido(puntosRecorrido) ?: return@LaunchedEffect

        runCatching {
            camaraRecorrido.animate(
                update = CameraUpdateFactory.newLatLngBounds(limites, 90),
                durationMs = 700
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(uiColors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .padding(bottom = 90.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EntrenamientoActivoBanner(
                navController = navController,
                modifier = Modifier.padding(top = 16.dp)
            )

            Text(
                text = "Predicción con IA",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = uiColors.textPrimary,
                modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
            )

            if (hayRecorrido) {
                IAInfoCard(
                    title = "Recorrido registrado",
                    subtitle = "Mapa del trayecto de tu última corrida",
                    uiColors = uiColors
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    GoogleMap(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        cameraPositionState = camaraRecorrido
                    ) {
                        // Trazo con borde: una linea gruesa oscura debajo y la
                        // linea de color encima. Da contraste sobre cualquier
                        // fondo del mapa, que de otro modo puede confundirse con
                        // calles o areas verdes.
                        Polyline(
                            points = puntosRecorrido,
                            width = 18f,
                            color = Color(0xFF00332F),
                            jointType = JointType.ROUND,
                            startCap = RoundCap(),
                            endCap = RoundCap()
                        )

                        Polyline(
                            points = puntosRecorrido,
                            width = 11f,
                            color = Color(0xFF26A69A),
                            jointType = JointType.ROUND,
                            startCap = RoundCap(),
                            endCap = RoundCap()
                        )

                        marcasKilometro.forEach { marca ->
                            Marker(
                                state = MarkerState(position = marca.posicion),
                                title = "${marca.kilometro} km",
                                icon = BitmapDescriptorFactory.defaultMarker(
                                    BitmapDescriptorFactory.HUE_AZURE
                                ),
                                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f)
                            )
                        }

                        Marker(
                            state = MarkerState(position = puntosRecorrido.first()),
                            title = "Inicio",
                            icon = BitmapDescriptorFactory.defaultMarker(
                                BitmapDescriptorFactory.HUE_GREEN
                            )
                        )

                        Marker(
                            state = MarkerState(position = puntosRecorrido.last()),
                            title = "Fin",
                            icon = BitmapDescriptorFactory.defaultMarker(
                                BitmapDescriptorFactory.HUE_RED
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (!settings.prediccionIAActiva) {
                IAInfoCard(
                    title = "IA desactivada",
                    subtitle = "La predicción automática está apagada desde Configuración.",
                    uiColors = uiColors
                ) {
                    Text(
                        text = "Los entrenamientos seguirán guardándose con datos reales, pero no se realizará predicción ni se mostrarán recomendaciones de IA.",
                        fontSize = 15.sp,
                        color = uiColors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            IAInfoCard(
                title = "Última corrida detectada",
                subtitle = "Datos finales recibidos desde Home y el reloj",
                uiColors = uiColors
            ) {
                IADataLine(
                    label = "Pace final",
                    value = if (pace.isNotBlank()) "$pace min/km" else "No disponible",
                    uiColors = uiColors
                )

                IADataLine(
                    label = "Tiempo final",
                    value = if (time.isNotBlank()) "$time s" else "No disponible",
                    uiColors = uiColors
                )

                IADataLine(
                    label = "Distancia final",
                    value = if (distance.isNotBlank()) "$distance km" else "No disponible",
                    uiColors = uiColors
                )

                IADataLine(
                    label = "Pasos finales",
                    value = if (steps.isNotBlank()) steps else "No disponible",
                    uiColors = uiColors
                )

                IADataLine(
                    label = "Estado",
                    value = if (RunDataStore.hasFinishedRun) {
                        "Corrida finalizada"
                    } else {
                        "No hay corrida finalizada"
                    },
                    uiColors = uiColors
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            IAInfoCard(
                title = "Parámetros reales del análisis",
                subtitle = "Valores usados como entrada del modelo",
                uiColors = uiColors
            ) {
                IAReadonlyField(
                    value = heartRate,
                    label = "Frecuencia cardiaca final",
                    uiColors = uiColors
                )

                Spacer(modifier = Modifier.height(12.dp))

                IAReadonlyField(
                    value = cadence,
                    label = "Cadencia final",
                    uiColors = uiColors
                )

                Spacer(modifier = Modifier.height(12.dp))

                IAReadonlyField(
                    value = acceleration,
                    label = "Aceleración final",
                    uiColors = uiColors
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { ejecutarPrediccionManual() },
                    enabled = settings.prediccionIAActiva,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = uiColors.primaryButton,
                        contentColor = uiColors.primaryButtonText,
                        disabledContainerColor = uiColors.cardSecondary,
                        disabledContentColor = uiColors.textMuted
                    )
                ) {
                    Text(
                        text = if (settings.prediccionIAActiva) {
                            "Recalcular predicción"
                        } else {
                            "IA desactivada"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            IAResultadoCard(
                resultadoTipo = resultadoTipo,
                mensaje = mensaje,
                clase = clase,
                etiqueta = etiqueta,
                confianza = confianza,
                recomendacion = recomendacion,
                uiColors = uiColors
            )

            if (settings.prediccionIAActiva && settings.mostrarProbabilidades) {
                Spacer(modifier = Modifier.height(16.dp))

                IAInfoCard(
                    title = "Probabilidades del modelo",
                    subtitle = "Distribución de confianza de la red neuronal",
                    uiColors = uiColors
                ) {
                    IAProbabilityLine(
                        label = "Ritmo bajo",
                        value = if (probBajo.isNotBlank()) probBajo else "No disponible",
                        uiColors = uiColors
                    )

                    IAProbabilityLine(
                        label = "Ritmo óptimo",
                        value = if (probOptimo.isNotBlank()) probOptimo else "No disponible",
                        uiColors = uiColors
                    )

                    IAProbabilityLine(
                        label = "Ritmo alto",
                        value = if (probAlto.isNotBlank()) probAlto else "No disponible",
                        uiColors = uiColors
                    )
                }
            }

            if (settings.prediccionIAActiva && !settings.mostrarProbabilidades) {
                Spacer(modifier = Modifier.height(16.dp))

                IAInfoCard(
                    title = "Probabilidades ocultas",
                    subtitle = "Esta opción está desactivada desde Configuración.",
                    uiColors = uiColors
                ) {
                    Text(
                        text = "La predicción seguirá funcionando, pero no se mostrarán los porcentajes del modelo.",
                        fontSize = 15.sp,
                        color = uiColors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }

        NavigationBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            containerColor = uiColors.bottomBar,
            contentColor = uiColors.bottomUnselected
        ) {
            NavigationBarItem(
                selected = false,
                onClick = { navController.irASeccion("home") },
                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                label = { Text("Home") },
                colors = iaBottomItemColors(uiColors)
            )

            NavigationBarItem(
                selected = true,
                onClick = { },
                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                label = { Text("IA") },
                colors = iaBottomItemColors(uiColors)
            )

            NavigationBarItem(
                selected = false,
                onClick = { navController.irASeccion("camera") },
                icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                label = { Text("Comida") },
                colors = iaBottomItemColors(uiColors)
            )

            NavigationBarItem(
                selected = false,
                onClick = { navController.irASeccion("agenda") },
                icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                label = { Text("Agenda") },
                colors = iaBottomItemColors(uiColors)
            )
        }
    }
}

@Composable
fun IAInfoCard(
    title: String,
    subtitle: String,
    uiColors: AppUiColors,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = uiColors.card
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(uiColors.card)
                .padding(18.dp)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = uiColors.textPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = uiColors.textMuted
            )

            Spacer(modifier = Modifier.height(14.dp))

            content()
        }
    }
}

@Composable
fun IAResultadoCard(
    resultadoTipo: Int,
    mensaje: String,
    clase: String,
    etiqueta: String,
    confianza: String,
    recomendacion: String,
    uiColors: AppUiColors
) {
    val cardColor = when (resultadoTipo) {
        0 -> uiColors.warning.copy(alpha = if (uiColors.background == Color(0xFF101114)) 0.18f else 0.28f)
        1 -> uiColors.success.copy(alpha = if (uiColors.background == Color(0xFF101114)) 0.18f else 0.28f)
        2 -> uiColors.dangerButton.copy(alpha = if (uiColors.background == Color(0xFF101114)) 0.18f else 0.25f)
        else -> uiColors.card
    }

    val borderColor = when (resultadoTipo) {
        0 -> uiColors.warning
        1 -> uiColors.success
        2 -> uiColors.dangerButton
        else -> uiColors.border
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardColor)
                .border(
                    width = 1.dp,
                    color = borderColor.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(18.dp)
        ) {
            Text(
                text = "Resultado de la predicción",
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = uiColors.textPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = mensaje,
                fontSize = 15.sp,
                color = uiColors.textSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            IADataLine(
                label = "Clase",
                value = if (clase.isNotBlank()) clase else "No disponible",
                uiColors = uiColors
            )

            IADataLine(
                label = "Etiqueta",
                value = if (etiqueta.isNotBlank()) etiqueta else "No disponible",
                uiColors = uiColors
            )

            IADataLine(
                label = "Confianza",
                value = if (confianza.isNotBlank()) confianza else "No disponible",
                uiColors = uiColors
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Recomendación:",
                fontSize = 14.sp,
                color = uiColors.textSecondary,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (recomendacion.isNotBlank()) recomendacion else "No disponible",
                fontSize = 15.sp,
                color = uiColors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun IAReadonlyField(
    value: String,
    label: String,
    uiColors: AppUiColors
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = {
            Text(
                text = label,
                color = uiColors.textMuted
            )
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        readOnly = true,
        singleLine = true
    )
}

@Composable
fun IADataLine(
    label: String,
    value: String,
    uiColors: AppUiColors
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = uiColors.textMuted
        )

        Text(
            text = value,
            fontSize = 15.sp,
            color = uiColors.textPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun IAProbabilityLine(
    label: String,
    value: String,
    uiColors: AppUiColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp)
            .background(
                color = uiColors.cardSecondary,
                shape = RoundedCornerShape(14.dp)
            )
            .border(
                width = 1.dp,
                color = uiColors.border,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = uiColors.textSecondary
        )

        Text(
            text = value,
            fontSize = 15.sp,
            color = uiColors.textPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun iaBottomItemColors(
    uiColors: AppUiColors
) = NavigationBarItemDefaults.colors(
    selectedIconColor = uiColors.textPrimary,
    selectedTextColor = uiColors.textPrimary,
    indicatorColor = uiColors.bottomSelected,
    unselectedIconColor = uiColors.bottomUnselected,
    unselectedTextColor = uiColors.bottomUnselected
)