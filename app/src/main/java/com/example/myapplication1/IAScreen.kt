package com.example.myapplication1

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.TrendingUp
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

    /**
     * Indica si hay algo que mostrar. Sin esto la pantalla dibujaba igual todas
     * sus tarjetas con "No disponible" repetido una docena de veces, que parece
     * una falla de la aplicacion cuando en realidad solo falta entrenar.
     */
    var hayDatos by remember { mutableStateOf(false) }

    /** Se esta consultando el historial del servidor. */
    var buscandoGuardado by remember { mutableStateOf(false) }

    /**
     * De donde salieron los datos en pantalla: del entrenamiento recien
     * terminado o de uno recuperado del historial. Conviene decirlo, porque no
     * es lo mismo analizar lo que acabas de hacer que algo de hace dias.
     */
    var origenDatos by remember { mutableStateOf("") }

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

        // Un entrenamiento sin ritmo no siempre significa que algo fallo. Si
        // ademas casi no hubo pasos, lo que paso es que la persona estuvo
        // quieta, y eso merece una respuesta propia: antes se le avisaba que
        // "faltaba el ritmo", como si el GPS se hubiera equivocado, cuando en
        // realidad no habia ritmo que medir.
        val cadenciaNumero = cadenceValor.toDoubleOrNull() ?: 0.0
        val tiempoNumero = timeValor.toDoubleOrNull() ?: 0.0

        if (paceValor.isBlank() && tiempoNumero >= 30.0 && cadenciaNumero < 20.0) {
            clase = ""
            etiqueta = "reposo"
            confianza = ""
            probBajo = ""
            probOptimo = ""
            probAlto = ""
            resultadoTipo = RESULTADO_REPOSO
            recomendacion = "Casi no hubo movimiento en esta sesión. Para que ALYRA " +
                    "pueda analizar tu ritmo, camina o trota al menos unos minutos seguidos."
            mensaje = "Sesión registrada sin desplazamiento: no hay un ritmo que clasificar."
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

    /**
     * Rellena la pantalla con el ultimo entrenamiento guardado en el servidor.
     *
     * [RunDataStore] solo existe en memoria, asi que se vacia en cuanto el
     * sistema cierra la aplicacion. Sin este respaldo, quien entrenaba y volvia
     * a abrir ALYRA encontraba la pantalla de IA en blanco pese a tener
     * entrenamientos guardados, y parecia que la IA no funcionaba.
     *
     * La prediccion se vuelve a calcular con el modelo actual en lugar de
     * mostrar la que quedo grabada ese dia: los entrenamientos antiguos traen el
     * resultado del modelo anterior, que no cubria caminata, y mezclarlos daria
     * respuestas incoherentes entre si.
     */
    fun cargarUltimoEntrenamientoGuardado() {
        val uid = SessionManager(context).getUserId()

        if (uid.isNullOrBlank()) {
            hayDatos = false
            limpiarPrediccion("Inicia sesión para ver el análisis de tus entrenamientos.")
            return
        }

        buscandoGuardado = true

        RetrofitClient.instance.getActivities(uid)
            .enqueue(object : retrofit2.Callback<List<ActivityItem>> {
                override fun onResponse(
                    call: retrofit2.Call<List<ActivityItem>>,
                    response: retrofit2.Response<List<ActivityItem>>
                ) {
                    buscandoGuardado = false

                    // El servidor los entrega del mas reciente al mas antiguo.
                    val ultima = response.body()?.firstOrNull()

                    if (!response.isSuccessful || ultima == null) {
                        hayDatos = false
                        limpiarPrediccion("Todavía no has terminado ningún entrenamiento.")
                        return
                    }

                    fun texto(valor: Double, decimales: Int): String =
                        if (valor > 0.0) String.format(Locale.US, "%.${decimales}f", valor) else ""

                    pace = texto(ultima.pace, 2)

                    // El servidor guarda la duracion en MINUTOS (ver el envio en
                    // homescreen y la forma en que la muestra la Agenda), pero
                    // tanto esta pantalla como el modelo la esperan en segundos:
                    // se entreno con tiempos de sesion completos, del orden de
                    // 1800. Sin esta conversion un entrenamiento de 67 s llegaba
                    // como 1.1 y se mostraba "1 s", ademas de alimentar a la red
                    // con una cifra fuera de toda escala.
                    time = texto(ultima.duration * 60.0, 0)

                    distance = texto(ultima.distance, 2)
                    heartRate = if (ultima.bpm > 0) ultima.bpm.toString() else ""
                    cadence = texto(ultima.cadence, 0)
                    acceleration = texto(ultima.acceleration, 2)
                    steps = if (ultima.steps > 0) ultima.steps.toString() else ""

                    hayDatos = true
                    origenDatos = if (ultima.date.isNotBlank()) {
                        "Último entrenamiento guardado · ${ultima.date}"
                    } else {
                        "Último entrenamiento guardado"
                    }

                    ejecutarPrediccionConDatos(
                        paceValor = pace,
                        timeValor = time,
                        heartRateValor = heartRate,
                        cadenceValor = cadence,
                        accelerationValor = acceleration
                    )
                }

                override fun onFailure(
                    call: retrofit2.Call<List<ActivityItem>>,
                    t: Throwable
                ) {
                    buscandoGuardado = false
                    hayDatos = false
                    limpiarPrediccion(
                        "No se pudo consultar tu historial. Revisa tu conexión e intenta de nuevo."
                    )
                }
            })
    }

    fun cargarDatosUltimaCorrida() {
        if (!settings.prediccionIAActiva) {
            hayDatos = false
            limpiarPrediccion("La IA está desactivada. Actívala en Configuración para analizar entrenamientos.")
            return
        }

        if (!RunDataStore.hasFinishedRun) {
            // Nada en memoria: se recurre al historial del servidor antes de
            // dar la pantalla por vacia.
            cargarUltimoEntrenamientoGuardado()
            return
        }

        hayDatos = true
        origenDatos = "Entrenamiento que acabas de terminar"

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

            if (buscandoGuardado) {
                IAInfoCard(
                    title = "Buscando tu último entrenamiento",
                    subtitle = "Consultando el historial guardado",
                    uiColors = uiColors
                ) {
                    Text(
                        text = "Un momento...",
                        fontSize = 15.sp,
                        color = uiColors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            } else if (!hayDatos) {
                // Una sola tarjeta que explica que hacer, en lugar de repetir
                // "No disponible" en una docena de campos vacios.
                IAInfoCard(
                    title = "Aún no hay nada que analizar",
                    subtitle = mensaje,
                    uiColors = uiColors
                ) {
                    Text(
                        text = "Cuando termines un entrenamiento, ALYRA lo analizará aquí " +
                                "automáticamente: te dirá si tu ritmo fue bajo, óptimo o alto, " +
                                "y qué conviene ajustar.",
                        fontSize = 15.sp,
                        color = uiColors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { navController.irASeccion("home") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF26A69A),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Iniciar un entrenamiento",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { cargarDatosUltimaCorrida() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = uiColors.cardSecondary,
                            contentColor = uiColors.textPrimary
                        )
                    ) {
                        Text(text = "Buscar de nuevo", fontSize = 15.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (hayDatos) {
            IAInfoCard(
                title = "Última corrida detectada",
                subtitle = origenDatos.ifBlank { "Datos finales recibidos desde Home y el reloj" },
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
                        "Recuperado de tu historial"
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

            // En reposo la red neuronal no llega a consultarse, asi que mostrar
            // su distribucion en ceros haria creer que fallo cuando lo que pasa
            // es que no habia nada que clasificar.
            if (settings.prediccionIAActiva &&
                settings.mostrarProbabilidades &&
                resultadoTipo != RESULTADO_REPOSO
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                IAInfoCard(
                    title = "Probabilidades del modelo",
                    subtitle = "Distribución de confianza de la red neuronal",
                    uiColors = uiColors
                ) {
                    IAProbabilityLine(
                        label = "Ritmo bajo",
                        value = if (probBajo.isNotBlank()) probBajo else "0.00%",
                        uiColors = uiColors,
                        acento = Color(0xFF4DB6AC),
                        esGanadora = resultadoTipo == 0
                    )

                    IAProbabilityLine(
                        label = "Ritmo óptimo",
                        value = if (probOptimo.isNotBlank()) probOptimo else "0.00%",
                        uiColors = uiColors,
                        acento = Color(0xFF26A69A),
                        esGanadora = resultadoTipo == 1
                    )

                    IAProbabilityLine(
                        label = "Ritmo alto",
                        value = if (probAlto.isNotBlank()) probAlto else "0.00%",
                        uiColors = uiColors,
                        acento = uiColors.warning,
                        esGanadora = resultadoTipo == 2
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
            } // fin de: if (hayDatos)

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

/**
 * Reposo. No es una clase del modelo -la red solo distingue ritmo bajo, optimo
 * y alto-, sino una situacion que se resuelve antes de consultarla: sin
 * desplazamiento no hay ritmo que clasificar.
 */
const val RESULTADO_REPOSO = 3

/** Nombre legible de cada clase, en lugar de la etiqueta cruda del modelo. */
private fun tituloDeClase(resultadoTipo: Int): String = when (resultadoTipo) {
    0 -> "Ritmo bajo"
    1 -> "Ritmo óptimo"
    2 -> "Ritmo alto"
    RESULTADO_REPOSO -> "En reposo"
    else -> "Sin analizar"
}

/**
 * Convierte "97.35%" en 97.35. Las cifras llegan ya formateadas desde la
 * pantalla, y aqui hacen falta como numero para dibujar las barras.
 */
private fun porcentajeNumerico(texto: String): Float =
    texto.removeSuffix("%").trim().replace(',', '.').toFloatOrNull()?.coerceIn(0f, 100f) ?: 0f

/**
 * Tarjeta del resultado de la red neuronal.
 *
 * Antes mostraba la salida cruda del modelo -"Clase 0", "Etiqueta ritmo_bajo"-,
 * que no significa nada para quien usa la aplicacion. Ahora el protagonista es
 * el nombre legible del ritmo y la recomendacion, y los datos tecnicos quedan
 * como detalle al pie.
 */
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
    // Un ritmo alto no es un error, asi que no se pinta de rojo de alarma: cada
    // clase tiene un color que la distingue sin dramatizar.
    val acento = when (resultadoTipo) {
        0 -> Color(0xFF4DB6AC)
        1 -> Color(0xFF26A69A)
        2 -> uiColors.warning
        RESULTADO_REPOSO -> Color(0xFF80CBC4)
        else -> uiColors.textMuted
    }

    val icono = when (resultadoTipo) {
        0 -> Icons.Default.TrendingUp
        1 -> Icons.Default.CheckCircle
        2 -> Icons.Default.LocalFireDepartment
        RESULTADO_REPOSO -> Icons.Default.Bedtime
        else -> Icons.Default.AutoAwesome
    }

    val hayResultado = resultadoTipo in 0..2

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        // El color va solo aqui. Antes tambien se pintaba en la Column interna y,
        // al llevar transparencia, el doble pintado oscurecia el centro y dejaba
        // a la vista un recuadro dentro de otro.
        colors = CardDefaults.cardColors(containerColor = uiColors.card)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // Franja superior con el veredicto, que es lo que el usuario busca.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(acento.copy(alpha = 0.16f))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(acento),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icono,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tu ritmo fue",
                        fontSize = 13.sp,
                        color = uiColors.textSecondary
                    )
                    Text(
                        text = tituloDeClase(resultadoTipo),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = uiColors.textPrimary
                    )
                }
            }

            Column(modifier = Modifier.padding(18.dp)) {

                if (hayResultado && confianza.isNotBlank()) {
                    val valor = porcentajeNumerico(confianza)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Confianza del modelo",
                            fontSize = 13.sp,
                            color = uiColors.textSecondary
                        )
                        Text(
                            text = confianza,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = acento
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(uiColors.border.copy(alpha = 0.30f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(valor / 100f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(acento)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (recomendacion.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(uiColors.cardSecondary, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = acento,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = recomendacion,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = uiColors.textPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                Text(
                    text = mensaje,
                    fontSize = 12.sp,
                    color = uiColors.textMuted
                )

                // Dato tecnico al pie: util para la demostracion, sin robarle
                // protagonismo a lo que de verdad le importa al usuario.
                if (hayResultado && etiqueta.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Salida del modelo: $etiqueta (clase $clase)",
                        fontSize = 11.sp,
                        color = uiColors.textMuted
                    )
                }
            }
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

/**
 * Una fila de la distribucion de probabilidad, con barra.
 *
 * Tres porcentajes sueltos obligan a compararlos mentalmente; la barra deja ver
 * de un vistazo cual domina y por cuanto, que es justo lo que se quiere mostrar
 * al explicar como decide la red.
 */
@Composable
fun IAProbabilityLine(
    label: String,
    value: String,
    uiColors: AppUiColors,
    acento: Color = Color(0xFF26A69A),
    esGanadora: Boolean = false
) {
    val porcentaje = porcentajeNumerico(value)
    val color = if (esGanadora) acento else uiColors.textMuted

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = if (esGanadora) FontWeight.Bold else FontWeight.Normal,
                color = if (esGanadora) uiColors.textPrimary else uiColors.textSecondary
            )

            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = if (esGanadora) FontWeight.Bold else FontWeight.Medium,
                color = if (esGanadora) uiColors.textPrimary else uiColors.textSecondary
            )
        }

        Spacer(modifier = Modifier.height(5.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(uiColors.border.copy(alpha = 0.30f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((porcentaje / 100f).coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
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