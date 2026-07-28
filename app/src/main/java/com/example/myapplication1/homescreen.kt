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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
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

                if (accuracy > 18f) {
                    RunDataStore.currentTimeMs = System.currentTimeMillis()
                    return
                }

                val newPoint = LatLng(location.latitude, location.longitude)
                val anterior = ultimaUbicacionTelefono

                if (anterior == null) {
                    ultimaUbicacionTelefono = location

                    if (pathPoints.isEmpty()) {
                        pathPoints = pathPoints + newPoint
                    }

                    cameraPositionState.position =
                        CameraPosition.fromLatLngZoom(newPoint, 17f)

                    RunDataStore.currentTimeMs = System.currentTimeMillis()
                    return
                }

                val metros = anterior.distanceTo(location)
                val pasosActuales = DatosRelojStore.datos.value.pasos

                val diferenciaTiempoMs = location.time - anterior.time
                val segundosEntrePuntos = if (diferenciaTiempoMs > 0L) {
                    diferenciaTiempoMs / 1000f
                } else {
                    0f
                }

                when (
                    HomeSensorPrecisionUtils.evaluarPuntoGps(
                        accuracy = accuracy,
                        metrosEntrePuntos = metros,
                        segundosEntrePuntos = segundosEntrePuntos,
                        pasosActuales = pasosActuales
                    )
                ) {
                    // Se conserva la referencia anterior a proposito: asi el
                    // siguiente punto confiable mide el tramo completo y no se
                    // pierde el avance recorrido mientras se filtraba ruido.
                    HomeSensorPrecisionUtils.ResultadoGps.DESCARTAR -> Unit

                    HomeSensorPrecisionUtils.ResultadoGps.REANCLAR -> {
                        ultimaUbicacionTelefono = location
                    }

                    HomeSensorPrecisionUtils.ResultadoGps.SUMAR -> {
                        RunDataStore.phoneDistanceMeters += metros
                        ultimaUbicacionTelefono = location
                        pathPoints = pathPoints + newPoint

                        cameraPositionState.position =
                            CameraPosition.fromLatLngZoom(newPoint, 17f)
                    }
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

    @SuppressLint("MissingPermission")
    fun iniciarEntrenamientoDesdeTelefono(
        enviarAlReloj: Boolean,
        validarPermiso: Boolean
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

        RunDataStore.iniciarNuevoEntrenamiento()

        // El servicio se arranca despues de fijar el estado para que la primera
        // notificacion ya muestre el entrenamiento en curso.
        EntrenamientoService.iniciar(context)
    }

    fun reanudarEntrenamientoDesdeTelefono(
        enviarAlReloj: Boolean,
        validarPermiso: Boolean
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
        RunDataStore.reanudarEntrenamiento()

        // Si el servicio se hubiera detenido, se vuelve a levantar al reanudar.
        EntrenamientoService.iniciar(context)
    }

    fun pausarEntrenamientoDesdeTelefono(enviarAlReloj: Boolean = true) {
        if (enviarAlReloj) {
            enviarComandoEntrenamiento(
                context = context,
                accion = ACCION_PAUSAR,
                origen = ORIGEN_TELEFONO
            )
        }

        fusedLocationClient.removeLocationUpdates(locationCallback)
        ultimaUbicacionTelefono = null

        RunDataStore.pausarEntrenamiento()
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

        // Clasificacion del ritmo con el modelo de IA, para que el entrenamiento
        // quede registrado junto con su analisis y no solo con las metricas crudas.
        // Solo se ejecuta cuando las entradas son validas: si el pace, los latidos
        // o la cadencia no se pudieron calcular, la actividad se guarda sin
        // prediccion en lugar de almacenar un resultado sin sustento.
        val prediccionIA: PaceResult? = if (
            settings.prediccionIAActiva &&
            finalPace > 0.0 &&
            bpmReloj > 0 &&
            cadenciaFinal > 0.0
        ) {
            runCatching {
                PaceClassifierProvider.obtener(context).predict(
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

    fun finalizarEntrenamientoDesdeTelefono(enviarAlReloj: Boolean = true) {
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

        val distanciaTelefonoKm = RunDataStore.phoneDistanceMeters / 1000.0
        val distanciaRelojKm = datosReloj.distancia.toDouble()

        val finalDistanceKm = HomeSensorPrecisionUtils.seleccionarDistanciaSegura(
            distanciaTelefonoKm = distanciaTelefonoKm,
            distanciaRelojKm = distanciaRelojKm,
            pasos = datosReloj.pasos
        )

        val finalPace = HomeSensorPrecisionUtils.calcularPaceSeguro(
            distanciaKm = finalDistanceKm,
            tiempoSegundos = finalTimeSeconds,
            pasos = datosReloj.pasos
        )

        val finalCadence = HomeSensorPrecisionUtils.calcularCadencia(
            pasos = datosReloj.pasos,
            tiempoSegundos = finalTimeSeconds
        )

        val finalAcceleration = HomeSensorPrecisionUtils.limpiarAceleracion(
            aceleracionFiltrada
        )

        val actividadFinal = detectarActividadHome(
            bpm = datosReloj.bpm,
            pasos = datosReloj.pasos,
            tiempoSegundos = finalTimeSeconds,
            aceleracion = finalAcceleration,
            pace = finalPace
        )

        if (pathPoints.size >= 2) {
            finishedPathPoints = pathPoints
            RunDataStore.finishedPathPoints = pathPoints
        }

        RunDataStore.finalizarEntrenamiento()

        // Al finalizar se retira la notificacion y se libera el servicio.
        EntrenamientoService.detener(context)

        if (settings.guardarUltimaCorrida && settings.prediccionIAActiva) {
            RunDataStore.lastPace = finalPace.toFloat()
            RunDataStore.lastTime = finalTimeSeconds.toFloat()
            RunDataStore.lastDistance = finalDistanceKm.toFloat()
            RunDataStore.lastBpm = datosReloj.bpm.toFloat()
            RunDataStore.lastSteps = datosReloj.pasos
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
            bpmReloj = datosReloj.bpm,
            pasosReloj = datosReloj.pasos,
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
                                        validarPermiso = true
                                    )
                                }

                                ACCION_PAUSAR -> {
                                    pausarEntrenamientoDesdeTelefono(
                                        enviarAlReloj = false
                                    )
                                }

                                ACCION_REANUDAR -> {
                                    reanudarEntrenamientoDesdeTelefono(
                                        enviarAlReloj = false,
                                        validarPermiso = true
                                    )
                                }

                                ACCION_FINALIZAR -> {
                                    finalizarEntrenamientoDesdeTelefono(
                                        enviarAlReloj = false
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

    LaunchedEffect(isTracking) {
        while (RunDataStore.isTracking) {
            RunDataStore.currentTimeMs = System.currentTimeMillis()
            delay(1000)
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

    val timeSeconds = RunDataStore.obtenerTiempoActualSegundos()

    val distanceKmTelefono = RunDataStore.phoneDistanceMeters / 1000.0
    val distanceKmReloj = datosReloj.distancia.toDouble()

    val distanceKmMostrada = HomeSensorPrecisionUtils.seleccionarDistanciaSegura(
        distanciaTelefonoKm = distanceKmTelefono,
        distanciaRelojKm = distanceKmReloj,
        pasos = datosReloj.pasos
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
        pasos = datosReloj.pasos
    )

    val paceMostrado = if (settings.unidadPrincipal == "millas") {
        HomeSensorPrecisionUtils.calcularPaceSeguro(
            distanciaKm = distanceMostrada,
            tiempoSegundos = timeSeconds,
            pasos = datosReloj.pasos
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

    val sincronizacionTexto = if (datosReloj.timestamp > 0L) {
        "Reloj sincronizado correctamente"
    } else {
        "Esperando sincronización del reloj"
    }

    LaunchedEffect(
        paceMostradoKm,
        timeSeconds,
        distanceKmMostrada,
        isTracking,
        datosReloj.bpm,
        datosReloj.pasos,
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
                                value = "${timeSeconds}s",
                                uiColors = uiColors
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = dimens.horizontalPadding),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatItem(
                                title = "BPM",
                                value = bpmTexto,
                                uiColors = uiColors
                            )

                            StatItem(
                                title = "Pasos",
                                value = datosReloj.pasos.toString(),
                                uiColors = uiColors
                            )

                            StatItem(
                                title = "Acel.",
                                value = aceleracionTexto,
                                uiColors = uiColors
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
                    pasos = datosReloj.pasos,
                    aceleracionTexto = aceleracionTexto,
                    sincronizacionTexto = sincronizacionTexto,
                    ultimaActividadTexto = ultimaActividadTexto,
                    mostrarIA = settings.prediccionIAActiva,
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
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = "Mi perfil",
                    tint = Color.White
                )
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
        pace = ritmo
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MiniStatCard(
                    modifier = Modifier.weight(1f),
                    label = "BPM",
                    value = bpmTexto,
                    uiColors = uiColors
                )

                MiniStatCard(
                    modifier = Modifier.weight(1f),
                    label = "Pasos",
                    value = pasos.toString(),
                    uiColors = uiColors
                )

                MiniStatCard(
                    modifier = Modifier.weight(1f),
                    label = "Acel.",
                    value = aceleracionTexto,
                    uiColors = uiColors
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        ModernCard(
            title = if (mostrarIA) "Actividad detectada" else "IA desactivada",
            subtitle = if (mostrarIA) {
                "Clasificación en tiempo real usando BPM, pasos, cadencia y aceleración"
            } else {
                "Activa la predicción automática en Configuración"
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
                value = "BPM: $bpmTexto | Pasos: $pasos | Tiempo: ${tiempoSegundos}s | Aceleración: $aceleracionTexto | Ritmo: $ritmoTexto",
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

fun formatearTiempo(segundos: Long): String {
    val min = segundos / 60
    val sec = segundos % 60
    return String.format(Locale.US, "%02d:%02d", min, sec)
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

fun detectarActividadHome(
    bpm: Int,
    pasos: Int,
    tiempoSegundos: Long,
    aceleracion: Float,
    pace: Double
): ActividadDetectadaHome {
    val cadencia = HomeSensorPrecisionUtils.calcularCadencia(
        pasos = pasos,
        tiempoSegundos = tiempoSegundos
    )

    if (tiempoSegundos < 8) {
        return ActividadDetectadaHome(
            estado = "Preparando sensores",
            consejo = "Espera unos segundos para estabilizar BPM, pasos y aceleración.",
            intensidad = 0
        )
    }

    if (pasos == 0 && aceleracion < 0.70f) {
        return ActividadDetectadaHome(
            estado = "Reposo",
            consejo = "Estás en reposo. No se detecta movimiento real.",
            intensidad = 0
        )
    }

    if (pasos == 0 && bpm in 45..115 && aceleracion < 0.90f) {
        return ActividadDetectadaHome(
            estado = "Reposo",
            consejo = "Frecuencia cardiaca estable y sin pasos detectados.",
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
        if (pace > 25.0) return 0.0

        return pace
    }

    fun paceTexto(
        pace: Double
    ): String {
        return if (pace > 0.0) {
            String.format(Locale.US, "%.2f", pace)
        } else {
            "--"
        }
    }

    fun seleccionarDistanciaSegura(
        distanciaTelefonoKm: Double,
        distanciaRelojKm: Double,
        pasos: Int
    ): Double {
        if (pasos <= 0) return 0.0

        if (distanciaRelojKm > 0.005) {
            return distanciaRelojKm
        }

        if (distanciaTelefonoKm > 0.005) {
            return distanciaTelefonoKm
        }

        return 0.0
    }

    fun limpiarAceleracion(
        aceleracion: Float
    ): Float {
        return when {
            aceleracion < 0f -> 0f
            aceleracion > 6f -> 0f
            else -> aceleracion
        }
    }

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

    /**
     * Resultado de evaluar una lectura del GPS frente a la ultima confiable.
     *
     * La distincion entre [DESCARTAR] y [REANCLAR] es la que evita perder
     * distancia: al descartar se conserva el punto de referencia anterior, de
     * modo que el siguiente punto valido mide el tramo completo en lugar de
     * solo su ultima parte.
     */
    enum class ResultadoGps {
        /** Lectura confiable: su distancia se suma al recorrido. */
        SUMAR,

        /** Lectura poco confiable o movimiento aun insuficiente: se conserva la referencia. */
        DESCARTAR,

        /** Pasó demasiado tiempo: se toma como nueva referencia sin sumar distancia. */
        REANCLAR
    }

    /**
     * Piso minimo de desplazamiento, en metros.
     *
     * El umbral real es el mayor entre este valor y la precision informada por
     * el GPS: si el receptor declara un error de 5 m, cualquier movimiento
     * menor a 5 m es indistinguible de su propio ruido y sumarlo infla la
     * distancia. Con umbral fijo de 1.2 m el error caminando despacio llegaba
     * a superar el 20%.
     */
    private const val MINIMO_METROS = 2.5f

    /** Velocidad maxima creible corriendo, en metros por segundo (27 km/h). */
    private const val MAXIMA_VELOCIDAD_MPS = 7.5f

    /** Por debajo de esta velocidad se asume que el usuario esta detenido. */
    private const val MINIMA_VELOCIDAD_MPS = 0.25f

    /** Antigüedad maxima de la referencia antes de volver a anclarla. */
    private const val MAXIMOS_SEGUNDOS_REFERENCIA = 30f

    fun evaluarPuntoGps(
        accuracy: Float,
        metrosEntrePuntos: Float,
        segundosEntrePuntos: Float,
        pasosActuales: Int
    ): ResultadoGps {
        // Sin pasos registrados no hay desplazamiento real: lo que mueve al
        // punto es la deriva del GPS, no el usuario.
        if (pasosActuales <= 0) return ResultadoGps.DESCARTAR

        if (accuracy > 18f) return ResultadoGps.DESCARTAR

        // Una referencia muy antigua ya no sirve para estimar velocidad; se
        // reancla sin sumar para no arrastrar un tramo sin respaldo.
        if (segundosEntrePuntos > MAXIMOS_SEGUNDOS_REFERENCIA) return ResultadoGps.REANCLAR

        if (segundosEntrePuntos <= 0f) return ResultadoGps.DESCARTAR

        val velocidadMps = metrosEntrePuntos / segundosEntrePuntos

        // Salto imposible: error tipico del GPS entre edificios.
        if (velocidadMps > MAXIMA_VELOCIDAD_MPS) return ResultadoGps.DESCARTAR

        // Usuario detenido: se conserva la referencia para no acumular deriva.
        if (velocidadMps < MINIMA_VELOCIDAD_MPS) return ResultadoGps.DESCARTAR

        // Movimiento aun dentro del margen de error del receptor: se conserva la
        // referencia y se acumula hasta que el desplazamiento supere ese margen.
        val minimoExigido = maxOf(MINIMO_METROS, accuracy)
        if (metrosEntrePuntos < minimoExigido) return ResultadoGps.DESCARTAR

        return ResultadoGps.SUMAR
    }
}