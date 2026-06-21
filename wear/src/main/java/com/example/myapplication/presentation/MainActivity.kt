package com.example.myapplication.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

const val PATH_DATOS_ENTRENAMIENTO = "/datos_entrenamiento"
const val PATH_CONTROL_ENTRENAMIENTO = "/control_entrenamiento"

const val ACCION_INICIAR = "INICIAR`"
const val ACCION_PAUSAR = "PAUSAR"
const val ACCION_REANUDAR = "REANUDAR"
const val ACCION_FINALIZAR = "FINALIZAR"

const val ORIGEN_TELEFONO = "TELEFONO"
const val ORIGEN_RELOJ = "RELOJ"

class MainActivity : ComponentActivity() {

    private val permisosLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            setContent {
                PantallaReloj()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        pedirPermisos()

        setContent {
            PantallaReloj()
        }
    }

    private fun pedirPermisos() {
        val permisos = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACTIVITY_RECOGNITION
        )

        if (Build.VERSION.SDK_INT >= 36) {
            permisos.add("android.permission.health.READ_HEART_RATE")
        } else {
            permisos.add(Manifest.permission.BODY_SENSORS)
        }

        val faltanPermisos = permisos.any { permiso ->
            ContextCompat.checkSelfPermission(this, permiso) != PackageManager.PERMISSION_GRANTED
        }

        if (faltanPermisos) {
            permisosLauncher.launch(permisos.toTypedArray())
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun PantallaReloj() {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val healthServicesManager = remember {
        HealthServicesManager(context)
    }

    var estadoEntrenamiento by remember { mutableStateOf("EN_ESPERA") }

    var segundos by remember { mutableIntStateOf(0) }
    var tiempoAcumuladoMs by remember { mutableLongStateOf(0L) }
    var inicioSegmentoMs by remember { mutableLongStateOf(0L) }

    var bpm by remember { mutableIntStateOf(0) }
    var bpmSuma by remember { mutableIntStateOf(0) }
    var bpmMuestras by remember { mutableIntStateOf(0) }
    var bpmMaximo by remember { mutableIntStateOf(0) }

    var pasos by remember { mutableIntStateOf(0) }
    var pasosBase by remember { mutableIntStateOf(-1) }
    var pasosGuardados by remember { mutableIntStateOf(0) }
    var usaStepCounter by remember { mutableStateOf(true) }

    var distancia by remember { mutableFloatStateOf(0f) }

    var aceleracionActual by remember { mutableFloatStateOf(0f) }
    var aceleracionPromedio by remember { mutableFloatStateOf(0f) }
    var aceleracionMaxima by remember { mutableFloatStateOf(0f) }
    var aceleracionSuma by remember { mutableFloatStateOf(0f) }
    var aceleracionMuestras by remember { mutableIntStateOf(0) }

    var ultimaUbicacion by remember { mutableStateOf<Location?>(null) }

    var estadoIA by remember { mutableStateOf("En espera") }
    var consejoIA by remember { mutableStateOf("Presiona iniciar entrenamiento") }

    var mensajeBpm by remember { mutableStateOf("Sensor cardiaco en espera") }
    var mensajeGps by remember { mutableStateOf("GPS en espera") }
    var mensajePasos by remember { mutableStateOf("Pasos en espera") }

    var ultimoEnvio by remember { mutableLongStateOf(0L) }
    var ultimoComandoProcesado by remember { mutableLongStateOf(0L) }

    fun bpmPromedioActual(): Int {
        return if (bpmMuestras > 0) {
            (bpmSuma / bpmMuestras.toFloat()).toInt()
        } else {
            bpm
        }
    }

    fun registrarBpm(nuevoBpm: Int) {
        if (nuevoBpm !in 35..220) return

        bpm = nuevoBpm
        bpmSuma += nuevoBpm
        bpmMuestras += 1

        if (nuevoBpm > bpmMaximo) {
            bpmMaximo = nuevoBpm
        }
    }

    fun registrarAceleracion(valor: Float) {
        val limpio = PrecisionWearUtils.limpiarAceleracion(valor)

        aceleracionActual = if (aceleracionActual <= 0f) {
            limpio
        } else {
            PrecisionWearUtils.suavizar(
                anterior = aceleracionActual,
                nuevo = limpio
            )
        }

        aceleracionSuma += aceleracionActual
        aceleracionMuestras += 1

        aceleracionPromedio = if (aceleracionMuestras > 0) {
            aceleracionSuma / aceleracionMuestras
        } else {
            0f
        }

        if (aceleracionActual > aceleracionMaxima) {
            aceleracionMaxima = aceleracionActual
        }
    }

    fun calcularPaceActual(): Float {
        return PrecisionWearUtils.calcularPaceSeguro(
            distanciaKm = distancia,
            segundos = segundos,
            pasos = pasos
        )
    }

    fun calcularCadenciaActual(): Float {
        return PrecisionWearUtils.calcularCadencia(
            pasos = pasos,
            segundos = segundos
        )
    }

    fun enviarEstadoActual() {
        val bpmPromedio = bpmPromedioActual()
        val paceSeguro = calcularPaceActual()
        val cadencia = calcularCadenciaActual()

        enviarDatosAlTelefono(
            context = context,
            bpm = bpmPromedio,
            bpmActual = bpm,
            bpmMaximo = bpmMaximo,
            pasos = pasos,
            distancia = distancia,
            aceleracion = aceleracionPromedio,
            aceleracionActual = aceleracionActual,
            tiempoSegundos = segundos.toLong(),
            pace = paceSeguro,
            cadencia = cadencia,
            estadoEntrenamiento = estadoEntrenamiento,
            estadoIA = estadoIA,
            consejoIA = consejoIA
        )
    }

    fun reiniciarDatosEntrenamiento() {
        segundos = 0
        tiempoAcumuladoMs = 0L
        inicioSegmentoMs = System.currentTimeMillis()

        bpm = 0
        bpmSuma = 0
        bpmMuestras = 0
        bpmMaximo = 0

        pasos = 0
        pasosBase = -1
        pasosGuardados = 0

        distancia = 0f

        aceleracionActual = 0f
        aceleracionPromedio = 0f
        aceleracionMaxima = 0f
        aceleracionSuma = 0f
        aceleracionMuestras = 0

        ultimaUbicacion = null
        ultimoEnvio = 0L

        mensajePasos = "Esperando pasos reales"
    }

    fun iniciarEntrenamiento(enviarAlTelefono: Boolean) {
        if (estadoEntrenamiento == "CORRIENDO") return

        if (enviarAlTelefono) {
            enviarComandoEntrenamiento(
                context = context,
                accion = ACCION_INICIAR,
                origen = ORIGEN_RELOJ
            )
        }

        estadoEntrenamiento = "CORRIENDO"
        reiniciarDatosEntrenamiento()

        estadoIA = "Preparando sensores"
        consejoIA = "Espera unos segundos para estabilizar datos"
        mensajeBpm = "Iniciando sensor cardiaco..."
        mensajeGps = "Estabilizando GPS"

        healthServicesManager.iniciarEjercicio(
            onBpm = { nuevoBpm ->
                mainHandler.post {
                    registrarBpm(nuevoBpm)
                    mensajeBpm = "BPM real"
                    enviarEstadoActual()
                }
            },
            onEstado = { estado ->
                mainHandler.post {
                    mensajeBpm = estado
                }
            }
        )

        enviarEstadoActual()
    }

    fun pausarEntrenamiento(enviarAlTelefono: Boolean) {
        if (estadoEntrenamiento != "CORRIENDO") return

        if (enviarAlTelefono) {
            enviarComandoEntrenamiento(
                context = context,
                accion = ACCION_PAUSAR,
                origen = ORIGEN_RELOJ
            )
        }

        if (inicioSegmentoMs > 0L) {
            val ahora = System.currentTimeMillis()
            tiempoAcumuladoMs += ahora - inicioSegmentoMs
            segundos = (tiempoAcumuladoMs / 1000).toInt()
        }

        pasosGuardados = pasos
        pasosBase = -1
        inicioSegmentoMs = 0L
        ultimaUbicacion = null

        estadoEntrenamiento = "PAUSADO"
        estadoIA = "Entrenamiento pausado"
        consejoIA = "Puedes reanudar o finalizar"
        mensajeBpm = "Pausado"
        mensajeGps = "Pausado"
        mensajePasos = "Pausado"

        healthServicesManager.pausarEjercicio()

        enviarEstadoActual()
    }

    fun reanudarEntrenamiento(enviarAlTelefono: Boolean) {
        if (estadoEntrenamiento != "PAUSADO") return

        if (enviarAlTelefono) {
            enviarComandoEntrenamiento(
                context = context,
                accion = ACCION_REANUDAR,
                origen = ORIGEN_RELOJ
            )
        }

        estadoEntrenamiento = "CORRIENDO"
        inicioSegmentoMs = System.currentTimeMillis()

        pasosBase = -1
        ultimaUbicacion = null

        estadoIA = "Preparando sensores"
        consejoIA = "Entrenamiento reanudado"
        mensajeBpm = "Reanudando BPM..."
        mensajeGps = "Estabilizando GPS"
        mensajePasos = "Esperando pasos reales"

        healthServicesManager.reanudarEjercicio()

        enviarEstadoActual()
    }

    fun finalizarEntrenamiento(enviarAlTelefono: Boolean) {
        if (estadoEntrenamiento == "EN_ESPERA" || estadoEntrenamiento == "FINALIZADO") return

        if (enviarAlTelefono) {
            enviarComandoEntrenamiento(
                context = context,
                accion = ACCION_FINALIZAR,
                origen = ORIGEN_RELOJ
            )
        }

        if (estadoEntrenamiento == "CORRIENDO" && inicioSegmentoMs > 0L) {
            val ahora = System.currentTimeMillis()
            tiempoAcumuladoMs += ahora - inicioSegmentoMs
        }

        segundos = (tiempoAcumuladoMs / 1000).toInt()
        inicioSegmentoMs = 0L

        pasosGuardados = pasos
        pasosBase = -1
        ultimaUbicacion = null

        estadoEntrenamiento = "FINALIZADO"
        estadoIA = "Entrenamiento finalizado"
        consejoIA = "Guardando en el teléfono"
        mensajeBpm = "Finalizado"
        mensajeGps = "Finalizado"
        mensajePasos = "Finalizado"

        healthServicesManager.finalizarEjercicio()

        enviarEstadoActual()
    }

    LaunchedEffect(Unit) {
        WearRunState.accion.collect { accion ->
            accion ?: return@collect
            when (accion) {
                ACCION_INICIAR   -> if (estadoEntrenamiento != "CORRIENDO") iniciarEntrenamiento(enviarAlTelefono = false)
                ACCION_PAUSAR    -> pausarEntrenamiento(enviarAlTelefono = false)
                ACCION_REANUDAR  -> reanudarEntrenamiento(enviarAlTelefono = false)
                ACCION_FINALIZAR -> finalizarEntrenamiento(enviarAlTelefono = false)
            }
            WearRunState.consumir()
        }
    }

    LaunchedEffect(Unit) {
        delay(1500)
        enviarEstadoActual()
    }

    LaunchedEffect(estadoEntrenamiento) {
        while (estadoEntrenamiento == "CORRIENDO") {
            val ahora = System.currentTimeMillis()

            segundos = ((tiempoAcumuladoMs + (ahora - inicioSegmentoMs)) / 1000).toInt()

            estadoIA = evaluarRitmo(
                bpmPromedio = bpmPromedioActual(),
                bpmActual = bpm,
                aceleracionPromedio = aceleracionPromedio,
                pasos = pasos,
                segundos = segundos
            )

            consejoIA = generarConsejo(estadoIA)

            enviarEstadoActual()

            delay(1000)
        }
    }

    DisposableEffect(Unit) {
        val listener = com.google.android.gms.wearable.MessageClient.OnMessageReceivedListener { messageEvent ->
            if (messageEvent.path == PATH_CONTROL_ENTRENAMIENTO) {
                val accion = String(messageEvent.data)
                when (accion) {
                    ACCION_INICIAR   -> iniciarEntrenamiento(enviarAlTelefono = false)
                    ACCION_PAUSAR    -> pausarEntrenamiento(enviarAlTelefono = false)
                    ACCION_REANUDAR  -> reanudarEntrenamiento(enviarAlTelefono = false)
                    ACCION_FINALIZAR -> finalizarEntrenamiento(enviarAlTelefono = false)
                }
            }
        }

        Wearable.getMessageClient(context).addListener(listener)

        onDispose {
            Wearable.getMessageClient(context).removeListener(listener)
        }
    }

    DisposableEffect(estadoEntrenamiento) {

        if (estadoEntrenamiento != "CORRIENDO") {
            onDispose { }
        } else {

            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

            val sensorStepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            val sensorStepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

            usaStepCounter = sensorStepCounter != null

            val sensorMovimiento =
                sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
                    ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

            val sensorListener = object : SensorEventListener {

                override fun onSensorChanged(event: SensorEvent) {
                    when (event.sensor.type) {

                        Sensor.TYPE_STEP_COUNTER -> {
                            val totalPasos = event.values[0].toInt()

                            if (pasosBase == -1) {
                                pasosBase = totalPasos
                            }

                            val pasosCalculados = pasosGuardados + (totalPasos - pasosBase)

                            pasos = if (pasosCalculados >= 0) {
                                pasosCalculados
                            } else {
                                0
                            }

                            mensajePasos = if (pasos > 0) {
                                "Pasos reales"
                            } else {
                                "Esperando pasos reales"
                            }
                        }

                        Sensor.TYPE_STEP_DETECTOR -> {
                            if (!usaStepCounter) {
                                pasos += 1
                                mensajePasos = "Pasos reales"
                            }
                        }

                        Sensor.TYPE_LINEAR_ACCELERATION -> {
                            val x = event.values[0]
                            val y = event.values[1]
                            val z = event.values[2]

                            val magnitud = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

                            registrarAceleracion(magnitud)
                        }

                        Sensor.TYPE_ACCELEROMETER -> {
                            val x = event.values[0]
                            val y = event.values[1]
                            val z = event.values[2]

                            val magnitudConGravedad =
                                sqrt((x * x + y * y + z * z).toDouble()).toFloat()

                            val aceleracionAjustada = abs(magnitudConGravedad - 9.80665f)

                            registrarAceleracion(aceleracionAjustada)
                        }
                    }

                    val ahora = System.currentTimeMillis()

                    if (ahora - ultimoEnvio > 1000L) {
                        ultimoEnvio = ahora
                        enviarEstadoActual()
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            sensorStepCounter?.let {
                sensorManager.registerListener(
                    sensorListener,
                    it,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
            }

            if (sensorStepCounter == null) {
                sensorStepDetector?.let {
                    sensorManager.registerListener(
                        sensorListener,
                        it,
                        SensorManager.SENSOR_DELAY_NORMAL
                    )
                }
            }

            sensorMovimiento?.let {
                sensorManager.registerListener(
                    sensorListener,
                    it,
                    SensorManager.SENSOR_DELAY_GAME
                )
            }

            val locationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            val locationListener = LocationListener { nuevaUbicacion ->

                if (estadoEntrenamiento != "CORRIENDO") return@LocationListener

                if (segundos < 12) {
                    mensajeGps = "Estabilizando GPS"
                    ultimaUbicacion = nuevaUbicacion
                    return@LocationListener
                }

                if (pasos <= 0) {
                    mensajeGps = "GPS activo, esperando pasos"
                    ultimaUbicacion = nuevaUbicacion
                    return@LocationListener
                }

                val accuracy = if (nuevaUbicacion.hasAccuracy()) {
                    nuevaUbicacion.accuracy
                } else {
                    99f
                }

                if (accuracy > 18f) {
                    mensajeGps = "GPS con baja precisión"
                    return@LocationListener
                }

                val anterior = ultimaUbicacion

                if (anterior == null) {
                    ultimaUbicacion = nuevaUbicacion
                    mensajeGps = "GPS activo"
                    return@LocationListener
                }

                val metros = anterior.distanceTo(nuevaUbicacion)
                val diferenciaTiempoMs = nuevaUbicacion.time - anterior.time

                val segundosEntrePuntos = if (diferenciaTiempoMs > 0L) {
                    diferenciaTiempoMs / 1000f
                } else {
                    0f
                }

                val puntoValido = PrecisionWearUtils.puntoGpsValido(
                    accuracy = accuracy,
                    metrosEntrePuntos = metros,
                    segundosEntrePuntos = segundosEntrePuntos,
                    pasos = pasos
                )

                if (!puntoValido) {
                    mensajeGps = "GPS filtrando ruido"
                    ultimaUbicacion = nuevaUbicacion
                    return@LocationListener
                }

                distancia += metros / 1000f
                ultimaUbicacion = nuevaUbicacion
                mensajeGps = "GPS activo"
            }

            val tieneFineLocation =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

            val tieneCoarseLocation =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

            if (tieneFineLocation || tieneCoarseLocation) {
                try {
                    val provider = when {
                        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> {
                            LocationManager.GPS_PROVIDER
                        }

                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> {
                            LocationManager.NETWORK_PROVIDER
                        }

                        else -> null
                    }

                    if (provider != null) {
                        mensajeGps = "Estabilizando GPS"

                        locationManager.requestLocationUpdates(
                            provider,
                            2000L,
                            1f,
                            locationListener
                        )
                    } else {
                        mensajeGps = "Activa ubicación en el reloj"
                    }

                } catch (e: Exception) {
                    mensajeGps = "Error al activar GPS"
                    e.printStackTrace()
                }

            } else {
                mensajeGps = "Permiso de ubicación requerido"
            }

            onDispose {
                sensorManager.unregisterListener(sensorListener)

                try {
                    locationManager.removeUpdates(locationListener)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val minutos = segundos / 60
    val seg = segundos % 60
    val tiempo = "%02d:%02d".format(minutos, seg)

    val horaActual = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    val pace = calcularPaceActual()
    val cadencia = calcularCadenciaActual()
    val bpmPromedio = bpmPromedioActual()

    val estadoTexto = when (estadoEntrenamiento) {
        "CORRIENDO" -> "Entrenamiento activo"
        "PAUSADO" -> "Entrenamiento pausado"
        "FINALIZADO" -> "Entrenamiento finalizado"
        else -> "Listo para iniciar"
    }

    val scroll = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050E18)),
        contentAlignment = Alignment.Center
    ) {

        FondoReloj()
        BarraDerecha()
        BarraDerecha()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(start = 16.dp, end = 22.dp, top = 8.dp, bottom = 45.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = horaActual,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )

            EstadoEntrenamientoChip(
                estado = estadoTexto,
                estadoEntrenamiento = estadoEntrenamiento
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = tiempo,
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricaPrincipal(
                    titulo = "Distancia",
                    valor = String.format(Locale.US, "%.2f", distancia),
                    unidad = "km"
                )

                Spacer(modifier = Modifier.width(20.dp))

                MetricaPrincipal(
                    titulo = "Pasos",
                    valor = pasos.toString(),
                    unidad = ""
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            CardOscura {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "♥",
                        color = Color(0xFFFF5A6E),
                        fontSize = 33.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (bpm == 0) "--" else bpm.toString(),
                            color = Color.White,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "BPM actual",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MiniMetricaWear(
                        modifier = Modifier.weight(1f),
                        titulo = "Prom.",
                        valor = if (bpmPromedio > 0) bpmPromedio.toString() else "--"
                    )

                    MiniMetricaWear(
                        modifier = Modifier.weight(1f),
                        titulo = "Máx.",
                        valor = if (bpmMaximo > 0) bpmMaximo.toString() else "--"
                    )

                    MiniMetricaWear(
                        modifier = Modifier.weight(1f),
                        titulo = "Zona",
                        valor = zonaCardiaca(bpmPromedio)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            CardOscura {
                Text(
                    text = "Métricas de carrera",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MiniMetricaWear(
                        modifier = Modifier.weight(1f),
                        titulo = "Pace",
                        valor = if (pace > 0f) {
                            String.format(Locale.US, "%.2f", pace)
                        } else {
                            "--"
                        }
                    )

                    MiniMetricaWear(
                        modifier = Modifier.weight(1f),
                        titulo = "Cad.",
                        valor = if (cadencia > 0f) {
                            String.format(Locale.US, "%.0f", cadencia)
                        } else {
                            "--"
                        }
                    )

                    MiniMetricaWear(
                        modifier = Modifier.weight(1f),
                        titulo = "Acel.",
                        valor = String.format(Locale.US, "%.2f", aceleracionPromedio)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = mensajeGps,
                    color = Color(0xFFBDBDBD),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = mensajeBpm,
                    color = Color(0xFFBDBDBD),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = mensajePasos,
                    color = Color(0xFFBDBDBD),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            CardIAWear(
                estadoIA = estadoIA,
                consejoIA = consejoIA
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (estadoEntrenamiento) {
                "EN_ESPERA" -> {
                    BotonRelojGrande(
                        texto = "Iniciar",
                        color = Color(0xFF008BD9),
                        onClick = {
                            iniciarEntrenamiento(enviarAlTelefono = true)
                        }
                    )
                }

                "CORRIENDO" -> {
                    BotonRelojGrande(
                        texto = "Pausar",
                        color = Color(0xFFE67E22),
                        onClick = {
                            pausarEntrenamiento(enviarAlTelefono = true)
                        }
                    )
                }

                "PAUSADO" -> {
                    BotonRelojGrande(
                        texto = "Reanudar",
                        color = Color(0xFF27AE60),
                        onClick = {
                            reanudarEntrenamiento(enviarAlTelefono = true)
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    BotonRelojGrande(
                        texto = "Finalizar",
                        color = Color(0xFFC0392B),
                        onClick = {
                            finalizarEntrenamiento(enviarAlTelefono = true)
                        }
                    )
                }

                "FINALIZADO" -> {
                    ResumenFinalWear(
                        distancia = distancia,
                        tiempo = tiempo,
                        bpmPromedio = bpmPromedio,
                        bpmMaximo = bpmMaximo,
                        pasos = pasos,
                        pace = pace,
                        cadencia = cadencia,
                        aceleracionPromedio = aceleracionPromedio
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    BotonRelojGrande(
                        texto = "Nuevo entrenamiento",
                        color = Color(0xFF2F80ED),
                        onClick = {
                            iniciarEntrenamiento(enviarAlTelefono = true)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(25.dp))
        }
    }
}

@Composable
fun EstadoEntrenamientoChip(
    estado: String,
    estadoEntrenamiento: String
) {
    val colorFondo = when (estadoEntrenamiento) {
        "CORRIENDO" -> Color(0xFF123D2A)
        "PAUSADO" -> Color(0xFF4A3213)
        "FINALIZADO" -> Color(0xFF12314A)
        else -> Color(0xFF262626)
    }

    val colorTexto = when (estadoEntrenamiento) {
        "CORRIENDO" -> Color(0xFF51D88A)
        "PAUSADO" -> Color(0xFFFFB74D)
        "FINALIZADO" -> Color(0xFF64B5F6)
        else -> Color(0xFFBDBDBD)
    }

    Box(
        modifier = Modifier
            .background(
                color = colorFondo,
                shape = RoundedCornerShape(50.dp)
            )
            .border(
                width = 1.dp,
                color = colorTexto.copy(alpha = 0.45f),
                shape = RoundedCornerShape(50.dp)
            )
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = estado,
            color = colorTexto,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun MetricaPrincipal(
    titulo: String,
    valor: String,
    unidad: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = titulo,
            color = Color.Gray,
            fontSize = 13.sp
        )

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = valor,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )

            if (unidad.isNotBlank()) {
                Spacer(modifier = Modifier.width(2.dp))

                Text(
                    text = unidad,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
    }
}

@Composable
fun CardOscura(
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E1E1E),
                        Color(0xFF111111)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .border(
                width = 1.dp,
                color = Color(0xFF1A3A55),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
    }
}

@Composable
fun MiniMetricaWear(
    modifier: Modifier = Modifier,
    titulo: String,
    valor: String
) {
    Column(
        modifier = modifier
        .background(
            color = Color(0xFF0D2035),
            shape = RoundedCornerShape(16.dp)
        )
        .border(
            width = 1.dp,
            color = Color(0xFF1A3A55),
            shape = RoundedCornerShape(16.dp)
        )
            .padding(vertical = 8.dp, horizontal = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = titulo,
            color = Color.Gray,
            fontSize = 9.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = valor,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun CardIAWear(
    estadoIA: String,
    consejoIA: String
) {
    val colorEstado = colorEstado(estadoIA)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF081828),
                shape = RoundedCornerShape(24.dp)
            )
            .border(
                width = 1.dp,
                color = colorEstado.copy(alpha = 0.55f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(13.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "IA deportiva",
            color = Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = estadoIA,
            color = colorEstado,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = consejoIA,
            color = Color(0xFFBDBDBD),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ResumenFinalWear(
    distancia: Float,
    tiempo: String,
    bpmPromedio: Int,
    bpmMaximo: Int,
    pasos: Int,
    pace: Float,
    cadencia: Float,
    aceleracionPromedio: Float
) {
    CardOscura {
        Text(
            text = "Resumen final",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MiniMetricaWear(
                modifier = Modifier.weight(1f),
                titulo = "Km",
                valor = String.format(Locale.US, "%.2f", distancia)
            )

            MiniMetricaWear(
                modifier = Modifier.weight(1f),
                titulo = "Tiempo",
                valor = tiempo
            )

            MiniMetricaWear(
                modifier = Modifier.weight(1f),
                titulo = "Pasos",
                valor = pasos.toString()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MiniMetricaWear(
                modifier = Modifier.weight(1f),
                titulo = "BPM prom.",
                valor = if (bpmPromedio > 0) bpmPromedio.toString() else "--"
            )

            MiniMetricaWear(
                modifier = Modifier.weight(1f),
                titulo = "BPM máx.",
                valor = if (bpmMaximo > 0) bpmMaximo.toString() else "--"
            )

            MiniMetricaWear(
                modifier = Modifier.weight(1f),
                titulo = "Cad.",
                valor = if (cadencia > 0f) {
                    String.format(Locale.US, "%.0f", cadencia)
                } else {
                    "--"
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MiniMetricaWear(
                modifier = Modifier.weight(1f),
                titulo = "Pace final",
                valor = if (pace > 0f) {
                    String.format(Locale.US, "%.2f", pace)
                } else {
                    "--"
                }
            )

            MiniMetricaWear(
                modifier = Modifier.weight(1f),
                titulo = "Acel. prom.",
                valor = String.format(Locale.US, "%.2f", aceleracionPromedio)
            )
        }
    }
}

@Composable
fun BotonRelojGrande(
    texto: String,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = color,
                shape = RoundedCornerShape(28.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

fun zonaCardiaca(bpmPromedio: Int): String {
    return when {
        bpmPromedio <= 0 -> "--"
        bpmPromedio < 100 -> "Reposo"
        bpmPromedio < 130 -> "Ligera"
        bpmPromedio < 160 -> "Media"
        bpmPromedio < 180 -> "Alta"
        else -> "Máx."
    }
}

fun evaluarRitmo(
    bpmPromedio: Int,
    bpmActual: Int,
    aceleracionPromedio: Float,
    pasos: Int,
    segundos: Int
): String {
    val cadencia = PrecisionWearUtils.calcularCadencia(
        pasos = pasos,
        segundos = segundos
    )

    return when {
        bpmPromedio == 0 && bpmActual == 0 -> "Analizando ritmo"
        segundos < 10 -> "Preparando sensores"

        pasos == 0 && aceleracionPromedio < 0.45f -> "Reposo"

        cadencia in 1f..85f && bpmPromedio < 125 -> "Caminando"

        cadencia in 86f..135f || bpmPromedio in 125..150 -> "Trotando"

        cadencia > 135f || bpmPromedio > 150 -> "Corriendo"

        else -> "Analizando ritmo"
    }
}

fun generarConsejo(estado: String): String {
    return when (estado) {
        "Preparando sensores" -> "Espera unos segundos para estabilizar datos"
        "Reposo" -> "Sin movimiento real detectado"
        "Caminando" -> "Caminata detectada con datos reales"
        "Trotando" -> "Trote detectado. Mantén el ritmo"
        "Corriendo" -> "Carrera detectada. Controla tu frecuencia"
        else -> "Esperando datos reales"
    }
}

fun colorEstado(estado: String): Color {
    return when (estado) {
        "Corriendo" -> Color(0xFFFF9800)
        "Trotando" -> Color(0xFF2196F3)
        "Caminando" -> Color(0xFF4CAF50)
        "Reposo" -> Color(0xFFFFB74D)
        "Preparando sensores" -> Color(0xFF64B5F6)
        "Entrenamiento pausado" -> Color(0xFFFFB74D)
        "Entrenamiento finalizado" -> Color(0xFF64B5F6)
        "En espera" -> Color(0xFF4CAF50)
        else -> Color(0xFF4CAF50)
    }
}

fun enviarDatosAlTelefono(
    context: Context,
    bpm: Int,
    bpmActual: Int,
    bpmMaximo: Int,
    pasos: Int,
    distancia: Float,
    aceleracion: Float,
    aceleracionActual: Float,
    tiempoSegundos: Long,
    pace: Float,
    cadencia: Float,
    estadoEntrenamiento: String,
    estadoIA: String,
    consejoIA: String
) {
    val request = PutDataMapRequest.create(PATH_DATOS_ENTRENAMIENTO).apply {
        dataMap.putInt("bpm", bpm)
        dataMap.putInt("bpmActual", bpmActual)
        dataMap.putInt("bpmMaximo", bpmMaximo)
        dataMap.putInt("pasos", pasos)
        dataMap.putFloat("distancia", distancia)
        dataMap.putFloat("aceleracion", aceleracion)
        dataMap.putFloat("aceleracionActual", aceleracionActual)
        dataMap.putLong("tiempoSegundos", tiempoSegundos)
        dataMap.putFloat("pace", pace)
        dataMap.putFloat("cadencia", cadencia)
        dataMap.putString("estadoEntrenamiento", estadoEntrenamiento)
        dataMap.putString("estadoIA", estadoIA)
        dataMap.putString("consejoIA", consejoIA)
        dataMap.putLong("timestamp", System.currentTimeMillis())
        dataMap.putLong("nonce", System.nanoTime())
    }.asPutDataRequest().setUrgent()

    Wearable.getDataClient(context).putDataItem(request)
}

fun enviarComandoEntrenamiento(
    context: Context,
    accion: String,
    origen: String
) {
    Wearable.getNodeClient(context).connectedNodes
        .addOnSuccessListener { nodes ->
            for (node in nodes) {
                Wearable.getMessageClient(context).sendMessage(
                    node.id,
                    PATH_CONTROL_ENTRENAMIENTO,
                    accion.toByteArray()
                )
            }
        }
}
object PrecisionWearUtils {

    fun calcularCadencia(
        pasos: Int,
        segundos: Int
    ): Float {
        if (pasos <= 0 || segundos <= 0) return 0f

        val minutos = segundos / 60f

        if (minutos <= 0f) return 0f

        return pasos / minutos
    }

    fun calcularPaceSeguro(
        distanciaKm: Float,
        segundos: Int,
        pasos: Int
    ): Float {
        if (segundos < 15) return 0f
        if (pasos <= 0) return 0f
        if (distanciaKm < 0.03f) return 0f

        val minutos = segundos / 60f
        val pace = minutos / distanciaKm

        if (pace < 2.5f) return 0f
        if (pace > 25f) return 0f

        return pace
    }

    fun limpiarAceleracion(valor: Float): Float {
        return when {
            valor < 0f -> 0f
            valor < 0.03f -> 0f
            valor > 8f -> 8f
            else -> valor
        }
    }

    fun suavizar(
        anterior: Float,
        nuevo: Float
    ): Float {
        return (anterior * 0.75f) + (nuevo * 0.25f)
    }

    fun puntoGpsValido(
        accuracy: Float,
        metrosEntrePuntos: Float,
        segundosEntrePuntos: Float,
        pasos: Int
    ): Boolean {
        if (pasos <= 0) return false
        if (accuracy > 18f) return false
        if (metrosEntrePuntos < 1.2f) return false
        if (metrosEntrePuntos > 25f) return false
        if (segundosEntrePuntos <= 0f) return false

        val velocidadMps = metrosEntrePuntos / segundosEntrePuntos

        if (velocidadMps < 0.35f) return false
        if (velocidadMps > 7.5f) return false

        return true
    }
}

@Composable
fun FondoReloj() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF0A2540),
                    Color(0xFF050E18)
                ),
                center = center,
                radius = size.minDimension / 1.5f
            )
        )
    }
}

@Composable
fun BarraDerecha() {
    Canvas(modifier = Modifier.fillMaxSize()) {

        val stroke = Stroke(
            width = 5f,
            cap = StrokeCap.Round
        )

        drawArc(
            color = Color(0xFF069AF1),
            startAngle = -40f,
            sweepAngle = 24f,
            useCenter = false,
            topLeft = Offset(size.width * 0.77f, size.height * 0.20f),
            size = Size(size.width * 0.35f, size.height * 0.60f),
            style = stroke
        )

        drawArc(
            color = Color.DarkGray,
            startAngle = 0f,
            sweepAngle = 95f,
            useCenter = false,
            topLeft = Offset(size.width * 0.77f, size.height * 0.20f),
            size = Size(size.width * 0.35f, size.height * 0.60f),
            style = stroke
        )
    }
}