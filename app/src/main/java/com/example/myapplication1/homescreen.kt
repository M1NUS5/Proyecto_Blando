package com.example.myapplication1

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.location.Location
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import coil.compose.rememberAsyncImagePainter
import androidx.navigation.NavController
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Silencio del reloj a partir del cual se considera que ya no esta enviando.
 * Envia cada segundo, asi que quince deja margen de sobra ante un tropiezo.
 */
private const val MAXIMA_ESPERA_RELOJ_MS = 15_000L

/** Cierto mientras el reloj siga entregando lecturas. */
private fun relojActivo(datos: DatosReloj): Boolean =
    datos.timestamp > 0L &&
        System.currentTimeMillis() - datos.timestamp < MAXIMA_ESPERA_RELOJ_MS

/**
 * Pasos a usar: los del reloj mientras este enviando, o los del telefono en su
 * defecto. Se resuelve en un solo lugar para que la pantalla, el medidor de
 * distancia y el detector de actividad no puedan discrepar entre si.
 */
private fun pasosDisponibles(datos: DatosReloj, pasosDelTelefono: Int): Int =
    if (relojActivo(datos)) datos.pasos else pasosDelTelefono

@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val dimens = rememberResponsiveDimens()

    val settings by AppSettingsStore.settings.collectAsState()
    val uiColors = appUiColors(settings.temaOscuro)

    val datosReloj by DatosRelojStore.datos.collectAsState()

    val isTracking = RunDataStore.isTracking
    val estadoEntrenamiento = RunDataStore.estadoEntrenamiento

    var pathPoints by remember { mutableStateOf(emptyList<LatLng>()) }
    var finishedPathPoints by remember { mutableStateOf(emptyList<LatLng>()) }
    var ultimaUbicacionTelefono by remember { mutableStateOf<Location?>(null) }
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }

    val juarez = LatLng(31.6904, -106.4245)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(juarez, 15f)
    }

    var ultimoComandoProcesado by remember { mutableLongStateOf(0L) }

    var accionPendientePermiso by remember { mutableStateOf<String?>(null) }
    var permisoRecienConcedido by remember { mutableStateOf(false) }

    var aceleracionFiltrada by remember { mutableFloatStateOf(0f) }

    var ultimaActividadTexto by remember {
        mutableStateOf("No hay actividad guardada todavía")
    }

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    /** Acumula la distancia priorizando la velocidad que informa el receptor. */
    val medidorDistancia = remember { MedidorDistancia() }

    /** Cuenta pasos con los sensores del telefono cuando no hay reloj. */
    val contadorPasos = remember { ContadorPasosTelefono(context) }

    /**
     * Pasos que usa toda la pantalla. Se prefieren los del reloj mientras siga
     * enviando, porque van en la muneca y son mas fieles; si deja de hacerlo se
     * recurre a los del telefono, ya que tanto la distancia como el ritmo exigen
     * pasos como confirmacion de movimiento y sin ellos quedarian en cero.
     *
     * Se comprueba que el dato sea reciente y no solo que exista: el almacen
     * conserva lo ultimo que llego, de modo que un reloj apagado a media sesion
     * dejaria su cifra congelada y taparia al contador del telefono.
     */
    val pasosEfectivos = pasosDisponibles(datosReloj, contadorPasos.pasos)

    /**
     * Decide si se muestran las metricas que solo el reloj puede medir.
     *
     * La deteccion sigue siendo automatica; el ajuste de Configuracion solo
     * puede ocultarlas, nunca forzar que aparezcan cuando no hay datos. Asi,
     * dejarlo mal puesto no impide que la aplicacion registre el entrenamiento.
     */
    val relojEnviando = !settings.soloTelefono && relojActivo(datosReloj)

    /** Observa los ultimos segundos para distinguir movimiento de reposo. */
    val ventanaActividad = remember { VentanaActividad() }
    var cadenciaReciente by remember { mutableStateOf<Double?>(null) }
    var pasosRecientes by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        if (
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    location?.let {
                        val userPosition = LatLng(
                            it.latitude,
                            it.longitude
                        )
                        currentLocation = userPosition
                        cameraPositionState.position =
                            CameraPosition.fromLatLngZoom(
                                userPosition,
                                17f
                            )
                    }
                }
        }
    }


    val locationRequest = remember {
        LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            2000L
        ).build()
    }

    LaunchedEffect(datosReloj.aceleracion) {
        aceleracionFiltrada = HomeSensorPrecisionUtils.aceleracionSuavizada(
            anterior = aceleracionFiltrada,
            nueva = datosReloj.aceleracion
        )
    }

    // El muestreo va por reloj propio y no por cambios en los pasos: estar
    // quieto no genera ningun cambio, y es justo el caso que hay que detectar.
    // Si solo se midiera al variar el contador, la ventana se quedaria congelada
    // en el ultimo movimiento y nunca reportaria reposo.
    LaunchedEffect(estadoEntrenamiento) {
        if (estadoEntrenamiento != "CORRIENDO") {
            cadenciaReciente = null
            pasosRecientes = null
            return@LaunchedEffect
        }

        while (true) {
            val pasosAhora = pasosDisponibles(
                DatosRelojStore.datos.value,
                contadorPasos.pasos
            )

            ventanaActividad.registrar(pasosAhora)
            pasosRecientes = ventanaActividad.pasosRecientes()
            cadenciaReciente = ventanaActividad.cadenciaReciente()
            kotlinx.coroutines.delay(2_000L)
        }
    }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return

                if (!RunDataStore.isTracking) return

                val accuracy = if (location.hasAccuracy()) {
                    location.accuracy
                } else {
                    99f
                }

                // La lectura pasa siempre al medidor, aunque la precision sea
                // mala: el medidor integra la velocidad Doppler, que no depende
                // de que el receptor logre ubicarse, y ya descarta por su cuenta
                // lo que no sirve. Cortar aqui lo dejaba sin datos y, al abrirse
                // huecos entre lecturas aceptadas, calculaba velocidades
                // imposibles que tambien rechazaba: el resultado era una ruta
                // dibujada en el mapa con 0.00 km de distancia.
                val newPoint = LatLng(location.latitude, location.longitude)

                // Sin reloj se usan los pasos del telefono: el medidor los exige
                // como confirmacion de movimiento, y sin ellos no habria distancia.
                val pasosActuales = pasosDisponibles(
                    DatosRelojStore.datos.value,
                    contadorPasos.pasos
                )

                // El calculo de la distancia queda a cargo del medidor, que
                // prioriza la velocidad informada por el receptor sobre la
                // diferencia de posiciones. El trazo del mapa se sigue armando
                // aqui, porque para dibujar si se necesitan las coordenadas.
                val metrosSumados = medidorDistancia.procesar(location, pasosActuales)

                RunDataStore.phoneDistanceMeters = medidorDistancia.metrosAcumulados

                val anterior = ultimaUbicacionTelefono
                val separacion = anterior?.distanceTo(location) ?: Float.MAX_VALUE

                // El trazo del mapa si necesita una posicion decente, aunque la
                // distancia ya se haya contabilizado: un punto muy impreciso
                // dibujaria un salto que no ocurrio.
                val sirveParaDibujar = accuracy <= 35f

                // Se agrega un punto al trazo cuando hubo avance medido o cuando
                // el desplazamiento es lo bastante grande para no ensuciar la
                // linea con la deriva del receptor estando quieto.
                if (sirveParaDibujar &&
                    (anterior == null || metrosSumados > 0.0 || separacion >= maxOf(2.5f, accuracy))
                ) {
                    ultimaUbicacionTelefono = location
                    pathPoints = pathPoints + newPoint

                    cameraPositionState.position =
                        CameraPosition.fromLatLngZoom(newPoint, 17f)
                }

                RunDataStore.currentTimeMs = System.currentTimeMillis()
            }
        }
    }

    fun tienePermisoUbicacion(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            permisoRecienConcedido = true
        } else {
            Toast.makeText(
                context,
                "Se necesita permiso de ubicación",
                Toast.LENGTH_SHORT
            ).show()

            accionPendientePermiso = null
        }
    }

    // Desde Android 13 la notificacion del entrenamiento requiere permiso
    // explicito. Si el usuario lo rechaza el entrenamiento sigue registrandose
    // igual: solo se pierde la consulta rapida desde la barra de notificaciones,
    // por eso no se bloquea nada ni se insiste.
    val permisoNotificacionesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (!concedido) {
            Toast.makeText(
                context,
                "Sin permiso de notificaciones no verás el entrenamiento en la barra de estado.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val concedido = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!concedido) {
                permisoNotificacionesLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Permiso del contador de pasos del telefono, necesario desde Android 10.
    // Igual que el de notificaciones, no bloquea nada: quien entrena con reloj
    // no lo necesita, y sin el simplemente no se cuentan pasos desde el telefono.
    val permisoPasosLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (!concedido) {
            Toast.makeText(
                context,
                "Sin permiso de actividad física no se pueden contar pasos desde el teléfono.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && contadorPasos.disponible) {
            val concedido = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED

            if (!concedido) {
                permisoPasosLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun iniciarEntrenamientoDesdeTelefono(
        enviarAlReloj: Boolean,
        validarPermiso: Boolean,
        instanteMs: Long = 0L
    ) {
        if (validarPermiso && !tienePermisoUbicacion()) {
            accionPendientePermiso = ACCION_INICIAR
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        if (enviarAlReloj) {
            enviarComandoEntrenamiento(
                context = context,
                accion = ACCION_INICIAR,
                origen = ORIGEN_TELEFONO
            )
        }

        pathPoints = emptyList()
        finishedPathPoints = emptyList()
        ultimaUbicacionTelefono = null
        aceleracionFiltrada = 0f
        medidorDistancia.reiniciar()
        ventanaActividad.reiniciar()
        cadenciaReciente = null
        pasosRecientes = null
        contadorPasos.iniciar()

        RunDataStore.iniciarNuevoEntrenamiento(instanteMs)

        // El servicio se arranca despues de fijar el estado para que la primera
        // notificacion ya muestre el entrenamiento en curso.
        EntrenamientoService.iniciar(context)
    }

    fun reanudarEntrenamientoDesdeTelefono(
        enviarAlReloj: Boolean,
        validarPermiso: Boolean,
        instanteMs: Long = 0L
    ) {
        if (validarPermiso && !tienePermisoUbicacion()) {
            accionPendientePermiso = ACCION_REANUDAR
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        if (enviarAlReloj) {
            enviarComandoEntrenamiento(
                context = context,
                accion = ACCION_REANUDAR,
                origen = ORIGEN_TELEFONO
            )
        }

        ultimaUbicacionTelefono = null
        contadorPasos.reanudar()
        RunDataStore.reanudarEntrenamiento(instanteMs)

        // Si el servicio se hubiera detenido, se vuelve a levantar al reanudar.
        EntrenamientoService.iniciar(context)
    }

    fun pausarEntrenamientoDesdeTelefono(enviarAlReloj: Boolean = true, instanteMs: Long = 0L) {
        if (enviarAlReloj) {
            enviarComandoEntrenamiento(
                context = context,
                accion = ACCION_PAUSAR,
                origen = ORIGEN_TELEFONO
            )
        }

        fusedLocationClient.removeLocationUpdates(locationCallback)
        ultimaUbicacionTelefono = null
        contadorPasos.pausar()

        RunDataStore.pausarEntrenamiento(instanteMs)
    }

    fun guardarActividadAutomatica(
        finalDistanceKm: Double,
        finalTimeSeconds: Long,
        finalPace: Double,
        bpmReloj: Int,
        pasosReloj: Int,
        aceleracionReloj: Float,
        estadoDetectado: String,
        consejoDetectado: String
    ) {
        val sessionManager = SessionManager(context)
        val userId = sessionManager.getUserId()

        if (userId.isNullOrBlank()) {
            Toast.makeText(context, "No hay sesión iniciada", Toast.LENGTH_SHORT).show()
            return
        }

        val fechaActual = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val cadenciaFinal = HomeSensorPrecisionUtils.calcularCadencia(
            pasos = pasosReloj,
            tiempoSegundos = finalTimeSeconds
        )

        val paceTextoNota = HomeSensorPrecisionUtils.paceTexto(finalPace)

        // Solo se clasifica cuando las entradas son validas: sin pace, latidos o
        // cadencia la actividad se guarda sin prediccion en lugar de inventarla.
        val prediccionIA: PaceResult? = if (
            settings.prediccionIAActiva &&
            finalPace > 0.0 &&
            bpmReloj > 0 &&
            cadenciaFinal > 0.0
        ) {
            runCatching {
                PaceClassifierProvider.obtener(context)?.predict(
                    PaceInput(
                        pace = finalPace.toFloat(),
                        heartRate = bpmReloj.toFloat(),
                        cadence = cadenciaFinal.toFloat(),
                        acceleration = aceleracionReloj,
                        time = finalTimeSeconds.toFloat()
                    )
                )
            }.getOrNull()
        } else {
            null
        }

        val recomendacionIA = prediccionIA?.let {
            recomendacionParaClase(it.predictedClass)
        }

        val analisisIANota = if (prediccionIA != null) {
            "Análisis IA: ${prediccionIA.label} " +
                "(${String.format(Locale.US, "%.1f", prediccionIA.confidence * 100f)}% de confianza)"
        } else {
            "Análisis IA: no disponible"
        }

        val notasConDatosReloj = """
            Actividad guardada automáticamente desde Home.
            Estado detectado: $estadoDetectado
            Consejo: $consejoDetectado
            BPM: ${if (bpmReloj > 0) bpmReloj else "No disponible"}
            Pasos: $pasosReloj
            Cadencia: ${String.format(Locale.US, "%.1f", cadenciaFinal)}
            Aceleración: ${String.format(Locale.US, "%.2f", aceleracionReloj)}
            Pace seguro: $paceTextoNota
            $analisisIANota
        """.trimIndent()

        val actividad = ActivityRequest(
            userId = userId,
            title = "Corrida automática",
            date = fechaActual,
            distance = finalDistanceKm,
            duration = finalTimeSeconds.toDouble() / 60.0,
            pace = finalPace,
            notes = notasConDatosReloj,
            bpm = bpmReloj,
            steps = pasosReloj,
            cadence = cadenciaFinal,
            acceleration = aceleracionReloj.toDouble(),
            iaClass = prediccionIA?.predictedClass,
            iaLabel = prediccionIA?.label ?: estadoDetectado,
            iaConfidence = prediccionIA?.confidence?.toDouble() ?: 0.0,
            iaRecommendation = recomendacionIA ?: consejoDetectado
        )

        RetrofitClient.instance.saveActivity(actividad)
            .enqueue(object : Callback<ActivityResponse> {
                override fun onResponse(
                    call: Call<ActivityResponse>,
                    response: Response<ActivityResponse>
                ) {
                    if (response.isSuccessful) {
                        ultimaActividadTexto =
                            "Entrenamiento • ${
                                String.format(Locale.US, "%.2f", finalDistanceKm)
                            } km • ${finalTimeSeconds}s • Estado: $estadoDetectado"

                        Toast.makeText(
                            context,
                            "Entrenamiento guardado en la base de datos",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "No se pudo guardar el entrenamiento",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<ActivityResponse>, t: Throwable) {
                    Toast.makeText(
                        context,
                        "Error al guardar entrenamiento",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    fun finalizarEntrenamientoDesdeTelefono(enviarAlReloj: Boolean = true, instanteMs: Long = 0L) {
        if (enviarAlReloj) {
            enviarComandoEntrenamiento(
                context = context,
                accion = ACCION_FINALIZAR,
                origen = ORIGEN_TELEFONO
            )
        }

        val finalTimeSeconds = RunDataStore.obtenerTiempoActualSegundos()

        fusedLocationClient.removeLocationUpdates(locationCallback)
        ultimaUbicacionTelefono = null

        // Los datos se leen de sus fuentes aqui mismo y no se toman de la
        // composicion.
        //
        // Cuando se finaliza desde el reloj, esta funcion la invoca un escucha
        // creado con `DisposableEffect(Unit)`, es decir una sola vez al abrir la
        // pantalla. Ese escucha conserva la version de la funcion de aquella
        // primera composicion, cuando todavia no habia llegado ningun dato: los
        // pasos valian cero, y sin pasos no hay distancia, ni ritmo, ni
        // cadencia. Por eso un entrenamiento terminado desde el reloj se
        // guardaba vacio y la IA lo clasificaba como reposo.
        val datosActuales = DatosRelojStore.datos.value
        val pasosFinales = pasosDisponibles(datosActuales, contadorPasos.pasos)

        val distanciaTelefonoKm = RunDataStore.phoneDistanceMeters / 1000.0
        val distanciaRelojKm = datosActuales.distancia.toDouble()

        val finalDistanceKm = HomeSensorPrecisionUtils.seleccionarDistanciaSegura(
            distanciaTelefonoKm = distanciaTelefonoKm,
            distanciaRelojKm = distanciaRelojKm,
            pasos = pasosFinales
        )

        val finalPace = HomeSensorPrecisionUtils.calcularPaceSeguro(
            distanciaKm = finalDistanceKm,
            tiempoSegundos = finalTimeSeconds,
            pasos = pasosFinales
        )

        val finalCadence = HomeSensorPrecisionUtils.calcularCadencia(
            pasos = pasosFinales,
            tiempoSegundos = finalTimeSeconds
        )

        val finalAcceleration = HomeSensorPrecisionUtils.limpiarAceleracion(
            aceleracionFiltrada
        )

        val actividadFinal = detectarActividadHome(
            bpm = datosActuales.bpm,
            pasos = pasosFinales,
            tiempoSegundos = finalTimeSeconds,
            aceleracion = finalAcceleration,
            pace = finalPace
        )

        if (pathPoints.size >= 2) {
            finishedPathPoints = pathPoints
            RunDataStore.finishedPathPoints = pathPoints
        }

        RunDataStore.finalizarEntrenamiento(instanteMs)

        // Al finalizar se retira la notificacion y se libera el servicio.
        EntrenamientoService.detener(context)
        contadorPasos.detener()

        if (settings.guardarUltimaCorrida && settings.prediccionIAActiva) {
            RunDataStore.lastPace = finalPace.toFloat()
            RunDataStore.lastTime = finalTimeSeconds.toFloat()
            RunDataStore.lastDistance = finalDistanceKm.toFloat()
            RunDataStore.lastBpm = datosActuales.bpm.toFloat()
            RunDataStore.lastSteps = pasosFinales
            RunDataStore.lastCadence = finalCadence.toFloat()
            RunDataStore.lastAcceleration = finalAcceleration
        }

        RunDataStore.hasFinishedRun = pathPoints.size >= 2

        RunDataStore.currentPace = finalPace.toFloat()
        RunDataStore.currentTime = finalTimeSeconds.toFloat()
        RunDataStore.currentDistance = finalDistanceKm.toFloat()

        guardarActividadAutomatica(
            finalDistanceKm = finalDistanceKm,
            finalTimeSeconds = finalTimeSeconds,
            finalPace = finalPace,
            bpmReloj = datosActuales.bpm,
            pasosReloj = pasosFinales,
            aceleracionReloj = finalAcceleration,
            estadoDetectado = actividadFinal.estado,
            consejoDetectado = actividadFinal.consejo
        )

        if (settings.prediccionIAActiva) {
            navController.irASeccion("IA")
        } else {
            Toast.makeText(
                context,
                "Entrenamiento guardado. IA desactivada.",
                Toast.LENGTH_SHORT
            ).show()

            navController.irASeccion("agenda")
        }
    }

    LaunchedEffect(permisoRecienConcedido) {
        if (permisoRecienConcedido) {
            when (accionPendientePermiso) {
                ACCION_INICIAR -> {
                    iniciarEntrenamientoDesdeTelefono(
                        enviarAlReloj = true,
                        validarPermiso = false
                    )
                }

                ACCION_REANUDAR -> {
                    reanudarEntrenamientoDesdeTelefono(
                        enviarAlReloj = true,
                        validarPermiso = false
                    )
                }
            }

            accionPendientePermiso = null
            permisoRecienConcedido = false
        }
    }

    DisposableEffect(Unit) {
        val listener = com.google.android.gms.wearable.DataClient.OnDataChangedListener { dataEvents ->
            for (event in dataEvents) {
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val item = event.dataItem

                    if (item.uri.path == PATH_CONTROL_ENTRENAMIENTO) {
                        val dataMap = DataMapItem.fromDataItem(item).dataMap

                        val accion = dataMap.getString("accion") ?: return@OnDataChangedListener
                        val origen = dataMap.getString("origen") ?: ""
                        val timestamp = dataMap.getLong("timestamp")

                        if (origen == ORIGEN_RELOJ && timestamp != ultimoComandoProcesado) {
                            ultimoComandoProcesado = timestamp

                            when (accion) {
                                ACCION_INICIAR -> {
                                    iniciarEntrenamientoDesdeTelefono(
                                        enviarAlReloj = false,
                                        validarPermiso = true,
                                        instanteMs = timestamp
                                    )
                                }

                                ACCION_PAUSAR -> {
                                    pausarEntrenamientoDesdeTelefono(
                                        enviarAlReloj = false,
                                        instanteMs = timestamp
                                    )
                                }

                                ACCION_REANUDAR -> {
                                    reanudarEntrenamientoDesdeTelefono(
                                        enviarAlReloj = false,
                                        validarPermiso = true,
                                        instanteMs = timestamp
                                    )
                                }

                                ACCION_FINALIZAR -> {
                                    finalizarEntrenamientoDesdeTelefono(
                                        enviarAlReloj = false,
                                        instanteMs = timestamp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Wearable.getDataClient(context).addListener(listener)

        onDispose {
            Wearable.getDataClient(context).removeListener(listener)
        }
    }

    DisposableEffect(isTracking) {
        if (isTracking && tienePermisoUbicacion()) {
            try {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    context.mainLooper
                )
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }

        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    // El sensor de pasos se libera al abandonar la pantalla para no dejarlo
    // registrado consumiendo bateria. Si el entrenamiento sigue activo, la
    // notificacion del servicio mantiene visible el avance.
    DisposableEffect(Unit) {
        onDispose { contadorPasos.detener() }
    }

    // Pulso que mantiene la pantalla al dia mientras esta a la vista.
    //
    // Antes solo latia durante el entrenamiento, y por eso al conectar el reloj
    // estando en reposo no pasaba nada: la pantalla no volvia a evaluarse y
    // seguia anunciando que medía con el telefono hasta pulsar iniciar o cambiar
    // de pestana. Latiendo siempre, el cambio de reloj se refleja solo.
    //
    // Medio segundo en lugar de uno para que el cronometro en pantalla no vaya
    // por detras del tiempo real que se guarda al finalizar.
    LaunchedEffect(Unit) {
        while (true) {
            RunDataStore.currentTimeMs = System.currentTimeMillis()
            delay(500)
        }
    }

    // Zoom al recorrido completo cuando se guarda finishedPathPoints
    LaunchedEffect(finishedPathPoints) {
        if (finishedPathPoints.size >= 2) {
            val boundsBuilder = LatLngBounds.builder()
            finishedPathPoints.forEach { boundsBuilder.include(it) }
            val bounds = boundsBuilder.build()
            val center = bounds.center
            // Calculamos zoom aproximado para ver todo el recorrido
            val latDiff = bounds.northeast.latitude - bounds.southwest.latitude
            val lngDiff = bounds.northeast.longitude - bounds.southwest.longitude
            val maxDiff = maxOf(latDiff, lngDiff)
            val zoom = when {
                maxDiff < 0.002 -> 17f
                maxDiff < 0.005 -> 16f
                maxDiff < 0.01  -> 15f
                maxDiff < 0.03  -> 14f
                maxDiff < 0.06  -> 13f
                else            -> 12f
            }
            cameraPositionState.position = CameraPosition.fromLatLngZoom(center, zoom)
        }
    }

    // Leer `currentTimeMs` suscribe la pantalla al pulso de un segundo del
    // cronometro, y es lo que hace que las cifras avancen solas.
    //
    // Antes ese redibujado lo provocaba, sin querer, la llegada de datos del
    // reloj: al actualizarse cada segundo arrastraba consigo al resto de la
    // pantalla. Entrenando sin reloj no cambiaba nada observable, asi que el
    // tiempo, los pasos y la distancia se quedaban congelados hasta cambiar de
    // pestana, que es cuando la pantalla se reconstruye entera.
    //
    // El calculo del tiempo sigue usando el reloj del sistema: si dependiera de
    // este valor, la notificacion del servicio se congelaria al salir de la
    // aplicacion, porque quien lo actualiza es esta pantalla.
    val timeSeconds = remember(RunDataStore.currentTimeMs, RunDataStore.isTracking) {
        RunDataStore.obtenerTiempoActualSegundos()
    }

    val distanceKmTelefono = RunDataStore.phoneDistanceMeters / 1000.0
    val distanceKmReloj = datosReloj.distancia.toDouble()

    val distanceKmMostrada = HomeSensorPrecisionUtils.seleccionarDistanciaSegura(
        distanciaTelefonoKm = distanceKmTelefono,
        distanciaRelojKm = distanceKmReloj,
        pasos = pasosEfectivos
    )

    val distanceMostrada = if (settings.unidadPrincipal == "millas") {
        distanceKmMostrada * 0.621371
    } else {
        distanceKmMostrada
    }

    val unidadDistancia = if (settings.unidadPrincipal == "millas") {
        "Mi"
    } else {
        "Km"
    }

    val paceMostradoKm = HomeSensorPrecisionUtils.calcularPaceSeguro(
        distanciaKm = distanceKmMostrada,
        tiempoSegundos = timeSeconds,
        pasos = pasosEfectivos
    )

    val paceMostrado = if (settings.unidadPrincipal == "millas") {
        HomeSensorPrecisionUtils.calcularPaceSeguro(
            distanciaKm = distanceMostrada,
            tiempoSegundos = timeSeconds,
            pasos = pasosEfectivos
        )
    } else {
        paceMostradoKm
    }

    val bpmTexto = if (datosReloj.bpm > 0) {
        datosReloj.bpm.toString()
    } else {
        "--"
    }

    val aceleracionSegura = HomeSensorPrecisionUtils.limpiarAceleracion(aceleracionFiltrada)
    val aceleracionTexto = HomeSensorPrecisionUtils.aceleracionTexto(aceleracionSegura)
    val ritmoTexto = HomeSensorPrecisionUtils.paceTexto(paceMostrado)

    val estadoTexto = when (estadoEntrenamiento) {
        "CORRIENDO" -> "Activo"
        "PAUSADO" -> "Pausado"
        "FINALIZADO" -> "Finalizado"
        else -> "En espera"
    }

    // Sin reloj la aplicacion funciona igual con los sensores del telefono, asi
    // que el aviso lo dice en esos terminos y no como si faltara algo.
    val sincronizacionTexto = if (relojEnviando) {
        "Reloj sincronizado correctamente"
    } else {
        "Midiendo con los sensores del teléfono"
    }

    LaunchedEffect(
        paceMostradoKm,
        timeSeconds,
        distanceKmMostrada,
        isTracking,
        datosReloj.bpm,
        pasosEfectivos,
        aceleracionSegura
    ) {
        RunDataStore.currentPace = paceMostradoKm.toFloat()
        RunDataStore.currentTime = timeSeconds.toFloat()
        RunDataStore.currentDistance = distanceKmMostrada.toFloat()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(uiColors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            HomeTopBar(
                navController = navController,
                temaOscuro = settings.temaOscuro
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(uiColors.background)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 90.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .padding(horizontal = dimens.horizontalPadding)
                        .fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = uiColors.mapPanel
                    )
                ) {
                    Column(
                        modifier = Modifier.background(uiColors.mapPanel)
                    ) {
                        GoogleMap(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(dimens.mapHeight),
                            cameraPositionState = cameraPositionState
                        ) {
                            // Mostrar ubicación actual siempre (sin tracking)
                            if (pathPoints.isEmpty() && finishedPathPoints.isEmpty()) {
                                currentLocation?.let {
                                    Marker(
                                        state = MarkerState(position = it),
                                        title = "Mi ubicación"
                                    )
                                }
                            }

                            // Recorrido en tiempo real durante el tracking
                            if (pathPoints.isNotEmpty()) {
                                Polyline(
                                    points = pathPoints,
                                    width = 10f,
                                    color = Color(0xFF26A69A)
                                )
                                Marker(
                                    state = MarkerState(position = pathPoints.first()),
                                    title = "Inicio"
                                )
                                Marker(
                                    state = MarkerState(position = pathPoints.last()),
                                    title = "Tú"
                                )
                            }

                            // Recorrido finalizado: mostrar inicio y fin
                            if (finishedPathPoints.size >= 2) {
                                Polyline(
                                    points = finishedPathPoints,
                                    width = 10f,
                                    color = Color(0xFF26A69A)
                                )
                                Marker(
                                    state = MarkerState(position = finishedPathPoints.first()),
                                    title = "Inicio"
                                )
                                Marker(
                                    state = MarkerState(position = finishedPathPoints.last()),
                                    title = "Fin"
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatItem(
                                title = unidadDistancia,
                                value = String.format(Locale.US, "%.2f", distanceMostrada),
                                uiColors = uiColors
                            )

                            StatItem(
                                title = "Ritmo",
                                value = ritmoTexto,
                                uiColors = uiColors
                            )

                            StatItem(
                                title = "Tiempo",
                                value = formatearTiempo(timeSeconds),
                                uiColors = uiColors
                            )
                        }

                        // El pulso y la aceleracion solo existen si hay reloj, asi
                        // que sin el se omiten en vez de mostrar "--" y "0.00".
                        // Al quedar un solo dato se centra, para que no se vea
                        // desplazado hacia un lado.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = dimens.horizontalPadding),
                            horizontalArrangement = if (relojEnviando) {
                                Arrangement.SpaceBetween
                            } else {
                                Arrangement.Center
                            }
                        ) {
                            if (relojEnviando) {
                                StatItem(
                                    title = "BPM",
                                    value = bpmTexto,
                                    uiColors = uiColors
                                )
                            }

                            StatItem(
                                title = "Pasos",
                                value = pasosEfectivos.toString(),
                                uiColors = uiColors
                            )

                            if (relojEnviando) {
                                StatItem(
                                    title = "Acel.",
                                    value = aceleracionTexto,
                                    uiColors = uiColors
                                )
                            }
                        }

                        // Antes de empezar se muestra que esta listo y que no,
                        // para no descubrir a media carrera que faltaba algo.
                        if (estadoEntrenamiento == "EN_ESPERA") {
                            PreparacionEntrenamiento(
                                ubicacionLista = tienePermisoUbicacion(),
                                pasosListos = contadorPasos.disponible || relojEnviando,
                                relojListo = relojEnviando,
                                soloTelefono = settings.soloTelefono,
                                uiColors = uiColors,
                                dimens = dimens
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            when (estadoEntrenamiento) {
                                "EN_ESPERA" -> {
                                    Button(
                                        onClick = {
                                            iniciarEntrenamientoDesdeTelefono(
                                                enviarAlReloj = true,
                                                validarPermiso = true
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = uiColors.primaryButton,
                                            contentColor = uiColors.primaryButtonText
                                        ),
                                        shape = RoundedCornerShape(18.dp)
                                    ) {
                                        Text("Iniciar")
                                    }
                                }

                                "CORRIENDO" -> {
                                    Button(
                                        onClick = {
                                            pausarEntrenamientoDesdeTelefono(
                                                enviarAlReloj = true
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = uiColors.warning,
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(18.dp)
                                    ) {
                                        Text("Detener")
                                    }
                                }

                                "PAUSADO" -> {
                                    Button(
                                        onClick = {
                                            reanudarEntrenamientoDesdeTelefono(
                                                enviarAlReloj = true,
                                                validarPermiso = true
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = uiColors.success,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(18.dp)
                                    ) {
                                        Text("Reanudar")
                                    }

                                    Button(
                                        onClick = {
                                            finalizarEntrenamientoDesdeTelefono(
                                                enviarAlReloj = true
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = uiColors.dangerButton,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(18.dp)
                                    ) {
                                        Text("Finalizar")
                                    }
                                }

                                "FINALIZADO" -> {
                                    Button(
                                        onClick = {
                                            iniciarEntrenamientoDesdeTelefono(
                                                enviarAlReloj = true,
                                                validarPermiso = true
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = uiColors.primaryButton,
                                            contentColor = uiColors.primaryButtonText
                                        ),
                                        shape = RoundedCornerShape(18.dp)
                                    ) {
                                        Text("Nuevo")
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                ResumenEntrenamientoSection(
                    estadoTexto = estadoTexto,
                    distanciaMostrada = distanceMostrada,
                    unidadDistancia = unidadDistancia,
                    tiempoSegundos = timeSeconds,
                    ritmo = paceMostrado,
                    ritmoTexto = ritmoTexto,
                    bpmTexto = bpmTexto,
                    pasos = pasosEfectivos,
                    aceleracionTexto = aceleracionTexto,
                    sincronizacionTexto = sincronizacionTexto,
                    ultimaActividadTexto = ultimaActividadTexto,
                    mostrarIA = settings.prediccionIAActiva,
                    cadenciaReciente = cadenciaReciente,
                    pasosRecientes = pasosRecientes,
                    relojEnviando = relojEnviando,
                    uiColors = uiColors
                )

                Spacer(modifier = Modifier.height(100.dp))
            }
        }

        NavigationBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            containerColor = uiColors.bottomBar,
            contentColor = uiColors.bottomUnselected
        ) {
            NavigationBarItem(selected = true, onClick = { navController.irASeccion("home") }, icon = { Icon(Icons.Default.Home, contentDescription = null) }, label = { Text("Home") }, colors = bottomItemColors(uiColors))
            NavigationBarItem(selected = false, onClick = { navController.irASeccion("IA") }, icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) }, label = { Text("IA") }, colors = bottomItemColors(uiColors))
            NavigationBarItem(selected = false, onClick = { navController.irASeccion("camera") }, icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) }, label = { Text("Comida") }, colors = bottomItemColors(uiColors))
            NavigationBarItem(selected = false, onClick = { navController.irASeccion("agenda") }, icon = { Icon(Icons.Default.DateRange, contentDescription = null) }, label = { Text("Agenda") }, colors = bottomItemColors(uiColors))
        }
    }
}

@Composable
fun HomeTopBar(
    navController: NavController,
    temaOscuro: Boolean
) {
    val dimens = rememberResponsiveDimens()
    val gradient = if (temaOscuro) {
        Brush.horizontalGradient(
            listOf(
                Color(0xFF0D1F1E),
                Color(0xFF26A69A)
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                Color(0xFF00695C),
                Color(0xFF00897B)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(gradient)
            .statusBarsPadding()
            .height(dimens.topBarHeight)
            .padding(horizontal = dimens.horizontalPadding, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.navigate("settings") }) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Configuración",
                    tint = Color.White
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(dimens.topBarLogoSize)
                        .background(Color.White, CircleShape)
                        .padding(3.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo_alyra),
                        contentDescription = "ALYRA logo",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ALYRA",
                    fontSize = dimens.topBarFontSize,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            IconButton(onClick = { navController.navigate("profile") }) {
                val fotoPerfil by PerfilStore.fotoUri.collectAsState()

                if (fotoPerfil != null) {
                    Image(
                        painter = rememberAsyncImagePainter(android.net.Uri.parse(fotoPerfil)),
                        contentDescription = "Mi perfil",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, Color.White, CircleShape)
                    )
                } else {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = "Mi perfil",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun ResumenEntrenamientoSection(
    estadoTexto: String,
    distanciaMostrada: Double,
    unidadDistancia: String,
    tiempoSegundos: Long,
    ritmo: Double,
    ritmoTexto: String,
    bpmTexto: String,
    pasos: Int,
    aceleracionTexto: String,
    sincronizacionTexto: String,
    ultimaActividadTexto: String,
    mostrarIA: Boolean,
    cadenciaReciente: Double? = null,
    pasosRecientes: Int? = null,
    relojEnviando: Boolean = true,
    uiColors: AppUiColors
) {
    val dimens = rememberResponsiveDimens()
    val bpmNumero = bpmTexto.toIntOrNull() ?: 0
    val aceleracionNumero = aceleracionTexto.toFloatOrNull() ?: 0f

    val actividadDetectada = detectarActividadHome(
        bpm = bpmNumero,
        pasos = pasos,
        tiempoSegundos = tiempoSegundos,
        aceleracion = aceleracionNumero,
        pace = ritmo,
        cadenciaReciente = cadenciaReciente,
        pasosRecientes = pasosRecientes
    )

    val cadencia = HomeSensorPrecisionUtils.calcularCadencia(
        pasos = pasos,
        tiempoSegundos = tiempoSegundos
    )

    val estadoMostrado = if (mostrarIA) {
        actividadDetectada.estado
    } else {
        "IA desactivada"
    }

    val consejoMostrado = if (mostrarIA) {
        actividadDetectada.consejo
    } else {
        "Activa la predicción automática en Configuración."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.horizontalPadding)
    ) {
        ModernCard(
            title = "Resumen en tiempo real",
            subtitle = "Datos actuales del entrenamiento",
            uiColors = uiColors
        ) {
            EstadoChip(
                estado = estadoTexto,
                uiColors = uiColors
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MiniStatCard(
                    modifier = Modifier.weight(1f),
                    label = unidadDistancia,
                    value = String.format(Locale.US, "%.2f", distanciaMostrada),
                    uiColors = uiColors
                )

                MiniStatCard(
                    modifier = Modifier.weight(1f),
                    label = "Tiempo",
                    value = formatearTiempo(tiempoSegundos),
                    uiColors = uiColors
                )

                MiniStatCard(
                    modifier = Modifier.weight(1f),
                    label = "Ritmo",
                    value = ritmoTexto,
                    uiColors = uiColors
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sin reloj no hay pulso ni aceleracion que mostrar; los pasos siguen
            // llegando desde el telefono y ocupan la fila completa.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (relojEnviando) {
                    MiniStatCard(
                        modifier = Modifier.weight(1f),
                        label = "BPM",
                        value = bpmTexto,
                        uiColors = uiColors
                    )
                }

                MiniStatCard(
                    modifier = Modifier.weight(1f),
                    label = "Pasos",
                    value = pasos.toString(),
                    uiColors = uiColors
                )

                if (relojEnviando) {
                    MiniStatCard(
                        modifier = Modifier.weight(1f),
                        label = "Acel.",
                        value = aceleracionTexto,
                        uiColors = uiColors
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        ModernCard(
            title = if (mostrarIA) "Actividad detectada" else "IA desactivada",
            subtitle = when {
                !mostrarIA -> "Activa la predicción automática en Configuración"
                relojEnviando -> "Clasificación en tiempo real usando BPM, pasos, cadencia y aceleración"
                else -> "Clasificación en tiempo real usando pasos y cadencia del teléfono"
            },
            uiColors = uiColors
        ) {
            InfoRow(
                label = "Estado detectado",
                value = estadoMostrado,
                uiColors = uiColors
            )

            Spacer(modifier = Modifier.height(10.dp))

            InfoRow(
                label = "Consejo",
                value = consejoMostrado,
                uiColors = uiColors
            )

            Spacer(modifier = Modifier.height(10.dp))

            InfoRow(
                label = "Cadencia estimada",
                value = if (cadencia > 0.0) {
                    "${String.format(Locale.US, "%.0f", cadencia)} pasos/min"
                } else {
                    "Sin movimiento suficiente"
                },
                uiColors = uiColors
            )

            Spacer(modifier = Modifier.height(10.dp))

            InfoRow(
                label = "Base del análisis",
                value = buildList {
                    if (relojEnviando) add("BPM: $bpmTexto")
                    add("Pasos: $pasos")
                    add("Tiempo: ${formatearTiempo(tiempoSegundos)}")
                    if (relojEnviando) add("Aceleración: $aceleracionTexto")
                    add("Ritmo: $ritmoTexto")
                }.joinToString(" | "),
                uiColors = uiColors
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (sincronizacionTexto.contains("correctamente")) {
                            uiColors.success.copy(alpha = 0.18f)
                        } else {
                            uiColors.warning.copy(alpha = 0.20f)
                        },
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = sincronizacionTexto,
                    fontSize = 14.sp,
                    color = if (sincronizacionTexto.contains("correctamente")) {
                        uiColors.success
                    } else {
                        uiColors.warning
                    },
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        ModernCard(
            title = "Última actividad guardada",
            subtitle = "Resumen rápido de la última sesión",
            uiColors = uiColors
        ) {
            Text(
                text = ultimaActividadTexto,
                fontSize = 15.sp,
                color = if (ultimaActividadTexto.contains("No hay")) {
                    uiColors.textMuted
                } else {
                    uiColors.textPrimary
                },
                fontWeight = if (ultimaActividadTexto.contains("No hay")) {
                    FontWeight.Normal
                } else {
                    FontWeight.Medium
                }
            )
        }
    }
}

@Composable
fun ModernCard(
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
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = uiColors.textPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = uiColors.textMuted
            )

            Spacer(modifier = Modifier.height(16.dp))

            content()
        }
    }
}

@Composable
fun MiniStatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    uiColors: AppUiColors
) {
    val dimens = rememberResponsiveDimens()
    Box(
        modifier = modifier
            .background(
                color = uiColors.cardSecondary,
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 1.dp,
                color = uiColors.border,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(vertical = 14.dp, horizontal = 10.dp)
    ) {
        Column {
            Text(
                text = label,
                fontSize = dimens.statLabelSize,
                color = uiColors.textMuted
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = value,
                fontSize = dimens.statValueSize,
                fontWeight = FontWeight.Bold,
                color = uiColors.textPrimary
            )
        }
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    uiColors: AppUiColors
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = uiColors.cardSecondary,
                shape = RoundedCornerShape(14.dp)
            )
            .border(
                width = 1.dp,
                color = uiColors.border,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = uiColors.textMuted
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = uiColors.textPrimary
        )
    }
}

@Composable
fun EstadoChip(
    estado: String,
    uiColors: AppUiColors
) {
    val estadoMinuscula = estado.lowercase(Locale.getDefault())

    val fondo = when (estadoMinuscula) {
        "activo", "corriendo", "entrenando" -> uiColors.success.copy(alpha = 0.18f)
        "pausado" -> uiColors.warning.copy(alpha = 0.22f)
        "finalizado" -> uiColors.primaryButton.copy(alpha = 0.22f)
        else -> uiColors.cardSecondary
    }

    val texto = when (estadoMinuscula) {
        "activo", "corriendo", "entrenando" -> uiColors.success
        "pausado" -> uiColors.warning
        "finalizado" -> uiColors.primaryButton
        else -> uiColors.textSecondary
    }

    Box(
        modifier = Modifier
            .background(
                color = fondo,
                shape = RoundedCornerShape(50.dp)
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = estado,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = texto
        )
    }
}

@Composable
fun bottomItemColors(
    uiColors: AppUiColors
) = NavigationBarItemDefaults.colors(
    selectedIconColor = uiColors.textPrimary,
    selectedTextColor = uiColors.textPrimary,
    indicatorColor = uiColors.bottomSelected,
    unselectedIconColor = uiColors.bottomUnselected,
    unselectedTextColor = uiColors.bottomUnselected
)

/**
 * Convierte segundos a `m:ss`, o a `h:mm:ss` cuando pasa de una hora.
 * Los minutos van sin cero delante para que se lea como un cronometro: 4:23.
 */
fun formatearTiempo(segundos: Long): String {
    val totales = segundos.coerceAtLeast(0L)

    val horas = totales / 3600
    val minutos = (totales % 3600) / 60
    val restantes = totales % 60

    return if (horas > 0) {
        String.format(Locale.US, "%d:%02d:%02d", horas, minutos, restantes)
    } else {
        String.format(Locale.US, "%d:%02d", minutos, restantes)
    }
}


/**
 * Repaso de lo que esta listo antes de iniciar.
 *
 * Evita el caso incomodo de terminar un recorrido y descubrir entonces que
 * faltaba un permiso o que el reloj no estaba enviando.
 */
@Composable
fun PreparacionEntrenamiento(
    ubicacionLista: Boolean,
    pasosListos: Boolean,
    relojListo: Boolean,
    soloTelefono: Boolean,
    uiColors: AppUiColors,
    dimens: ResponsiveDimens
) {
    // Con el modo de solo telefono activo, el reloj no cuenta como pendiente.
    val todoListo = ubicacionLista && pasosListos && (relojListo || soloTelefono)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.horizontalPadding)
            .padding(top = 4.dp)
    ) {
        Text(
            text = if (todoListo) "Todo listo para entrenar" else "Antes de empezar",
            fontSize = dimens.bodyFontSize,
            fontWeight = FontWeight.Bold,
            color = if (todoListo) uiColors.primaryButton else uiColors.textPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        FilaPreparacion(
            listo = ubicacionLista,
            titulo = "Ubicación y GPS",
            detalle = if (ubicacionLista) "Permiso concedido" else "Falta conceder el permiso",
            uiColors = uiColors,
            dimens = dimens
        )

        FilaPreparacion(
            listo = pasosListos,
            titulo = "Contador de pasos",
            detalle = when {
                relojListo -> "Desde el reloj"
                pasosListos -> "Desde el teléfono"
                else -> "Sin sensor disponible"
            },
            uiColors = uiColors,
            dimens = dimens
        )

        if (!soloTelefono) {
            FilaPreparacion(
                listo = relojListo,
                titulo = "Reloj",
                detalle = if (relojListo) {
                    "Enviando pulso y aceleración"
                } else {
                    "Abre ALYRA en el reloj, o activa Solo teléfono"
                },
                uiColors = uiColors,
                dimens = dimens
            )
        }
    }
}

@Composable
private fun FilaPreparacion(
    listo: Boolean,
    titulo: String,
    detalle: String,
    uiColors: AppUiColors,
    dimens: ResponsiveDimens
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(
                    if (listo) uiColors.primaryButton else uiColors.warning,
                    androidx.compose.foundation.shape.CircleShape
                )
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = titulo,
            fontSize = dimens.labelFontSize,
            fontWeight = FontWeight.Medium,
            color = uiColors.textPrimary
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = detalle,
            fontSize = dimens.labelFontSize,
            color = uiColors.textMuted,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun StatItem(
    title: String,
    value: String,
    uiColors: AppUiColors = appUiColors(false)
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            fontSize = 12.sp,
            color = uiColors.textMuted
        )

        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = uiColors.textPrimary
        )
    }
}

data class ActividadDetectadaHome(
    val estado: String,
    val consejo: String,
    val intensidad: Int
)

/**
 * Clasifica lo que la persona esta haciendo en este momento.
 *
 * [cadenciaReciente] son los pasos por minuto de los ultimos segundos, no el
 * promedio de la sesion: es lo que permite notar que alguien se detuvo. Vale
 * `null` sin historia suficiente, y entonces se usa la cadencia acumulada.
 */
fun detectarActividadHome(
    bpm: Int,
    pasos: Int,
    tiempoSegundos: Long,
    aceleracion: Float,
    pace: Double,
    cadenciaReciente: Double? = null,
    pasosRecientes: Int? = null
): ActividadDetectadaHome {
    val cadenciaAcumulada = HomeSensorPrecisionUtils.calcularCadencia(
        pasos = pasos,
        tiempoSegundos = tiempoSegundos
    )

    val cadencia = cadenciaReciente ?: cadenciaAcumulada

    if (tiempoSegundos < 8) {
        return ActividadDetectadaHome(
            estado = "Preparando sensores",
            consejo = "Espera unos segundos para estabilizar BPM, pasos y aceleración.",
            intensidad = 0
        )
    }

    // Sin pasos en la ventana reciente la persona esta quieta, aunque lleve
    // cientos acumulados de antes.
    val sinMovimientoReciente = when (pasosRecientes) {
        null -> pasos == 0
        else -> pasosRecientes == 0
    }

    // La aceleracion no decide el reposo: el reloj envia el promedio de la
    // sesion, que nunca vuelve a bajar. Los pasos recientes si lo distinguen.
    if (sinMovimientoReciente) {
        return ActividadDetectadaHome(
            estado = "Reposo",
            consejo = if (pasos > 0) {
                "Estás en reposo. Cuando quieras, retoma el movimiento."
            } else {
                "Estás en reposo. No se detecta movimiento real."
            },
            intensidad = 0
        )
    }

    // Movimiento muy escaso: ni quieto del todo ni caminando de verdad.
    if (cadenciaReciente != null && cadenciaReciente < 15.0) {
        return ActividadDetectadaHome(
            estado = "Casi detenido",
            consejo = "Apenas hay movimiento. Retoma el paso para seguir sumando.",
            intensidad = 0
        )
    }

    if (cadencia > 135.0 || bpm > 150 || (pace > 0.0 && pace <= 6.5 && bpm >= 135)) {
        return ActividadDetectadaHome(
            estado = "Corriendo",
            consejo = "Ritmo alto detectado. Controla tu frecuencia cardiaca.",
            intensidad = 3
        )
    }

    // Moderate intensity
    if (cadencia in 86.0..135.0 || bpm in 125..150 || (pace > 6.5 && pace <= 10.0 && bpm >= 115)) {
        return ActividadDetectadaHome(
            estado = "Trotando",
            consejo = "Ritmo moderado detectado. Mantén la respiración controlada.",
            intensidad = 2
        )
    }

    if (cadencia in 1.0..85.0) {
        return ActividadDetectadaHome(
            estado = "Caminando",
            consejo = "Movimiento ligero detectado. Ritmo estable de caminata.",
            intensidad = 1
        )
    }

    return ActividadDetectadaHome(
        estado = "Analizando",
        consejo = "Sigue moviéndote unos segundos para clasificar mejor la actividad.",
        intensidad = 1
    )
}

object HomeSensorPrecisionUtils {

    fun calcularCadencia(
        pasos: Int,
        tiempoSegundos: Long
    ): Double {
        if (pasos <= 0 || tiempoSegundos <= 0L) return 0.0

        val minutos = tiempoSegundos / 60.0

        if (minutos <= 0.0) return 0.0

        return pasos / minutos
    }

    fun calcularPaceSeguro(
        distanciaKm: Double,
        tiempoSegundos: Long,
        pasos: Int
    ): Double {
        if (tiempoSegundos < 15L) return 0.0
        if (distanciaKm < 0.03) return 0.0
        if (pasos <= 0) return 0.0

        val pace = (tiempoSegundos / 60.0) / distanciaKm

        if (pace < 2.5) return 0.0

        // El techo acompaña al rango con el que se entreno la red (30 min/km).
        // Un paseo lento con paradas llega a ese ritmo y es igual de valido.
        if (pace > PACE_MAXIMO) return 0.0

        return pace
    }

    /** Mismo limite superior que cubre el modelo entrenado. */
    private const val PACE_MAXIMO = 30.0

    fun paceTexto(
        pace: Double
    ): String {
        return if (pace > 0.0) {
            String.format(Locale.US, "%.2f", pace)
        } else {
            "--"
        }
    }

    /**
     * Elige la mayor de las dos distancias medidas.
     *
     * Ambos receptores solo pueden equivocarse por debajo -descartan tramos con
     * mala señal, nunca inventan recorrido- asi que la mayor es la mas completa.
     */
    fun seleccionarDistanciaSegura(
        distanciaTelefonoKm: Double,
        distanciaRelojKm: Double,
        pasos: Int
    ): Double {
        if (pasos <= 0) return 0.0

        val mayor = maxOf(distanciaTelefonoKm, distanciaRelojKm)

        if (mayor > 0.005) {
            return mayor
        }

        return 0.0
    }

    /**
     * Recorta la aceleracion al mismo techo que aplica el reloj
     * (`PrecisionWearUtils.limpiarAceleracion`). Recortar y no anular: un cero
     * lo interpreta la pantalla de IA como dato ausente.
     */
    fun limpiarAceleracion(
        aceleracion: Float
    ): Float {
        return when {
            aceleracion < 0f -> 0f
            aceleracion > MAXIMA_ACELERACION -> MAXIMA_ACELERACION
            else -> aceleracion
        }
    }

    /** Mismo techo que aplica el reloj antes de enviar el dato. */
    private const val MAXIMA_ACELERACION = 8f

    fun aceleracionSuavizada(
        anterior: Float,
        nueva: Float
    ): Float {
        if (nueva < 0f) return anterior

        val limpia = limpiarAceleracion(nueva)

        return if (anterior <= 0f) {
            limpia
        } else {
            (anterior * 0.75f) + (limpia * 0.25f)
        }
    }

    fun aceleracionTexto(
        aceleracion: Float
    ): String {
        val limpia = limpiarAceleracion(aceleracion)
        return String.format(Locale.US, "%.2f", limpia)
    }

}