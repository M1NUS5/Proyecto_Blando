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

const val ACCION_INICIAR = "INICIAR"
const val ACCION_PAUSAR = "PAUSAR"
const val ACCION_REANUDAR = "REANUDAR"
const val ACCION_FINALIZAR = "FINALIZAR"

const val ORIGEN_TELEFONO = "TELEFONO"
const val ORIGEN_RELOJ = "RELOJ"

/** Desfase maximo aceptado entre la marca del telefono y el reloj propio. */
private const val MAXIMO_DESFASE_ACEPTADO_MS = 10_000L

/**
 * Instante que se toma como referencia para contar el tiempo.
 *
 * Las ordenes que llegan del telefono traen el momento en que se pulsaron alli,
 * no el momento en que el reloj las recibe. Usar esa marca hace que ambos
 * midan desde el mismo punto de partida en lugar de separarse por lo que tardo
 * el mensaje, una diferencia que en cada pausa se sumaba a la anterior.
 *
 * Solo se acepta si concuerda con el reloj propio: si las horas de los dos
 * equipos difirieran mucho, fiarse de la ajena daria un tiempo absurdo.
 */
private fun instanteValidoWear(propuesto: Long): Long {
    val ahora = System.currentTimeMillis()

    if (propuesto <= 0L) return ahora
    if (propuesto > ahora) return ahora
    if (ahora - propuesto > MAXIMO_DESFASE_ACEPTADO_MS) return ahora

    return propuesto
}

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

    /** Ultimas lecturas crudas del sensor, para filtrar por mediana. */
    val bpmRecientes = remember { ArrayDeque<Int>() }
    var bpmUltimaLecturaMs by remember { mutableLongStateOf(0L) }
    var bpmRechazosSeguidos by remember { mutableIntStateOf(0) }

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

    /** Acumula la distancia priorizando la velocidad que informa el receptor. */
    val medidorDistancia = remember { MedidorDistancia() }

    /** Mismos ultimos segundos que observa el telefono, para coincidir con el. */
    val ventanaActividad = remember { VentanaActividad() }

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

    /**
     * Procesa una lectura del sensor cardiaco con dos filtros, porque el sensor
     * optico entrega valores sueltos erroneos al moverse el brazo:
     *
     * 1. Limite de variacion: descarta saltos imposibles para un pulso humano,
     *    y se resincroniza tras varios rechazos seguidos.
     * 2. Mediana movil: muestra la mediana de las ultimas lecturas, que ignora
     *    un dato aislado fuera de rango.
     */
    fun registrarBpm(nuevoBpm: Int) {
        if (nuevoBpm !in 35..220) return

        val ahora = System.currentTimeMillis()

        if (bpm > 0 && bpmUltimaLecturaMs > 0L) {
            val segundos = ((ahora - bpmUltimaLecturaMs) / 1000.0).coerceAtLeast(0.5)
            val cambioPermitido = (MAXIMO_CAMBIO_BPM_POR_SEGUNDO * segundos).coerceAtLeast(6.0)

            if (abs(nuevoBpm - bpm) > cambioPermitido &&
                bpmRechazosSeguidos < MAXIMOS_RECHAZOS_SEGUIDOS
            ) {
                bpmRechazosSeguidos += 1
                return
            }
        }

        bpmRechazosSeguidos = 0
        bpmUltimaLecturaMs = ahora

        bpmRecientes.addLast(nuevoBpm)
        if (bpmRecientes.size > VENTANA_MEDIANA_BPM) {
            bpmRecientes.removeFirst()
        }

        val bpmFiltrado = bpmRecientes.sorted()[bpmRecientes.size / 2]

        bpm = bpmFiltrado
        bpmSuma += bpmFiltrado
        bpmMuestras += 1

        if (bpmFiltrado > bpmMaximo) {
            bpmMaximo = bpmFiltrado
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

    fun reiniciarDatosEntrenamiento(instanteMs: Long = 0L) {
        segundos = 0
        tiempoAcumuladoMs = 0L
        inicioSegmentoMs = instanteValidoWear(instanteMs)

        bpm = 0
        bpmSuma = 0
        bpmMuestras = 0
        bpmMaximo = 0

        bpmRecientes.clear()
        bpmUltimaLecturaMs = 0L
        bpmRechazosSeguidos = 0

        pasos = 0
        pasosBase = -1
        pasosGuardados = 0

        distancia = 0f
        medidorDistancia.reiniciar()

        aceleracionActual = 0f
        aceleracionPromedio = 0f
        aceleracionMaxima = 0f
        aceleracionSuma = 0f
        aceleracionMuestras = 0

        ultimaUbicacion = null
        ultimoEnvio = 0L

        mensajePasos = "Esperando pasos reales"
    }

    fun iniciarEntrenamiento(enviarAlTelefono: Boolean, instanteMs: Long = 0L) {
        if (estadoEntrenamiento == "CORRIENDO") return

        if (enviarAlTelefono) {
            enviarComandoEntrenamiento(
                context = context,
                accion = ACCION_INICIAR,
                origen = ORIGEN_RELOJ
            )
        }

        // Mantiene sensores y GPS activos aunque se apague la pantalla.
        EntrenamientoWearService.iniciar(context)

        estadoEntrenamiento = "CORRIENDO"
        reiniciarDatosEntrenamiento(instanteMs)

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

    fun pausarEntrenamiento(enviarAlTelefono: Boolean, instanteMs: Long = 0L) {
        if (estadoEntrenamiento != "CORRIENDO") return

        if (enviarAlTelefono) {
            enviarComandoEntrenamiento(
                context = context,
                accion = ACCION_PAUSAR,
                origen = ORIGEN_RELOJ
            )
        }

        if (inicioSegmentoMs > 0L) {
            val ahora = instanteValidoWear(instanteMs)
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

    fun reanudarEntrenamiento(enviarAlTelefono: Boolean, instanteMs: Long = 0L) {
        if (estadoEntrenamiento != "PAUSADO") return

        // Si el servicio se hubiera detenido, se levanta de nuevo al reanudar.
        EntrenamientoWearService.iniciar(context)

        if (enviarAlTelefono) {
            enviarComandoEntrenamiento(
                context = context,
                accion = ACCION_REANUDAR,
                origen = ORIGEN_RELOJ
            )
        }

        estadoEntrenamiento = "CORRIENDO"
        inicioSegmentoMs = instanteValidoWear(instanteMs)

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

    fun finalizarEntrenamiento(enviarAlTelefono: Boolean, instanteMs: Long = 0L) {
        if (estadoEntrenamiento == "EN_ESPERA" || estadoEntrenamiento == "FINALIZADO") return

        if (enviarAlTelefono) {
            enviarComandoEntrenamiento(
                context = context,
                accion = ACCION_FINALIZAR,
                origen = ORIGEN_RELOJ
            )
        }

        // Libera el bloqueo de procesador y retira la notificacion.
        EntrenamientoWearService.detener(context)

        if (estadoEntrenamiento == "CORRIENDO" && inicioSegmentoMs > 0L) {
            val ahora = instanteValidoWear(instanteMs)
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

    LaunchedEffect(estadoEntrenamiento) {
        if (estadoEntrenamiento != "CORRIENDO") {
            ventanaActividad.reiniciar()
            return@LaunchedEffect
        }

        while (estadoEntrenamiento == "CORRIENDO") {
            val ahora = System.currentTimeMillis()

            segundos = ((tiempoAcumuladoMs + (ahora - inicioSegmentoMs)) / 1000).toInt()

            // Se muestrea por reloj propio y no al cambiar los pasos: estar
            // quieto no genera cambios, y es justo lo que hay que detectar.
            ventanaActividad.registrar(pasos, ahora)

            estadoIA = evaluarRitmo(
                bpmPromedio = bpmPromedioActual(),
                bpmActual = bpm,
                aceleracionPromedio = aceleracionPromedio,
                pasos = pasos,
                segundos = segundos,
                cadenciaReciente = ventanaActividad.cadenciaReciente()?.toFloat(),
                pasosRecientes = ventanaActividad.pasosRecientes(),
                pace = calcularPaceActual()
            )

            consejoIA = generarConsejo(estadoIA)

            enviarEstadoActual()

            delay(1000)
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

                        if (origen == ORIGEN_TELEFONO && timestamp != ultimoComandoProcesado) {
                            ultimoComandoProcesado = timestamp

                            mainHandler.post {
                                when (accion) {
                                    ACCION_INICIAR -> iniciarEntrenamiento(enviarAlTelefono = false, instanteMs = timestamp)
                                    ACCION_PAUSAR -> pausarEntrenamiento(enviarAlTelefono = false, instanteMs = timestamp)
                                    ACCION_REANUDAR -> reanudarEntrenamiento(enviarAlTelefono = false, instanteMs = timestamp)
                                    ACCION_FINALIZAR -> finalizarEntrenamiento(enviarAlTelefono = false, instanteMs = timestamp)
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

                        // El contador acumulado es la fuente mas confiable, pero el
                        // hardware lo entrega con retraso. Cuando llega, corrige el
                        // valor que el detector fue mostrando en vivo.
                        Sensor.TYPE_STEP_COUNTER -> {
                            val totalPasos = event.values[0].toInt()

                            if (pasosBase == -1) {
                                // La referencia se ajusta restando lo que el
                                // detector ya conto en esta sesion. Si el
                                // contador tarda en entregar su primera lectura,
                                // esos pasos seguirian siendo validos en lugar
                                // de reiniciarse a cero.
                                val yaContados = (pasos - pasosGuardados).coerceAtLeast(0)
                                pasosBase = totalPasos - yaContados
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

                        // El detector emite un evento por cada paso en cuanto ocurre.
                        // Es lo que hace que la cuenta se mueva de inmediato al
                        // caminar, en lugar de esperar a que el contador reporte.
                        Sensor.TYPE_STEP_DETECTOR -> {
                            pasos += 1
                            mensajePasos = "Pasos reales"
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

            // Los dos sensores de pasos a la vez: el detector responde al
            // instante y el contador acumulado corrige el total despues.
            // La latencia en cero evita que el reloj agrupe lecturas y la
            // pantalla parezca congelada.
            sensorStepCounter?.let {
                sensorManager.registerListener(
                    sensorListener,
                    it,
                    SensorManager.SENSOR_DELAY_NORMAL,
                    0
                )
            }

            sensorStepDetector?.let {
                sensorManager.registerListener(
                    sensorListener,
                    it,
                    SensorManager.SENSOR_DELAY_FASTEST,
                    0
                )
            }

            // La aceleracion se usa como promedio del entrenamiento, no para
            // detectar gestos, por lo que no requiere la frecuencia mas alta.
            // Con SENSOR_DELAY_GAME el reloj entregaba unas cincuenta lecturas
            // por segundo; ahora que el procesador se mantiene despierto durante
            // toda la sesion, esa frecuencia consumia bateria sin aportar
            // precision al promedio.
            sensorMovimiento?.let {
                sensorManager.registerListener(
                    sensorListener,
                    it,
                    SensorManager.SENSOR_DELAY_UI
                )
            }

            val locationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            val locationListener = LocationListener { nuevaUbicacion ->

                if (estadoEntrenamiento != "CORRIENDO") return@LocationListener

                if (segundos < 12) {
                    mensajeGps = "Estabilizando GPS"
                    return@LocationListener
                }

                if (pasos <= 0) {
                    mensajeGps = "GPS activo, esperando pasos"
                    return@LocationListener
                }

                // La lectura pasa siempre al medidor, aunque la precision sea
                // mala: integra la velocidad Doppler, que no depende de que el
                // receptor logre ubicarse, y ya descarta por su cuenta lo que no
                // sirve. Cortar aqui dejaba la distancia en cero pese a llevar
                // cientos de pasos contados.
                val metros = medidorDistancia.procesar(nuevaUbicacion, pasos)

                distancia = (medidorDistancia.metrosAcumulados / 1000.0).toFloat()
                ultimaUbicacion = nuevaUbicacion

                mensajeGps = if (metros > 0.0) "GPS activo" else "GPS filtrando ruido"
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
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {

        FondoReloj()
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
                        color = Color(0xFF26A69A),
                        onClick = {
                            iniciarEntrenamiento(enviarAlTelefono = true)
                        }
                    )
                }

                "CORRIENDO" -> {
                    BotonRelojGrande(
                        texto = "Detener",
                        color = Color(0xFFFFB74D),
                        onClick = {
                            pausarEntrenamiento(enviarAlTelefono = true)
                        }
                    )
                }

                "PAUSADO" -> {
                    BotonRelojGrande(
                        texto = "Reanudar",
                        color = Color(0xFF26A69A),
                        onClick = {
                            reanudarEntrenamiento(enviarAlTelefono = true)
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    BotonRelojGrande(
                        texto = "Finalizar",
                        color = Color(0xFFD9534F),
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
                        color = Color(0xFF26A69A),
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
        "CORRIENDO" -> Color(0xFF0D3330)
        "PAUSADO" -> Color(0xFF4A3213)
        "FINALIZADO" -> Color(0xFF0D2E2C)
        else -> Color(0xFF262626)
    }

    val colorTexto = when (estadoEntrenamiento) {
        "CORRIENDO" -> Color(0xFF4DB6AC)
        "PAUSADO" -> Color(0xFFFFB74D)
        "FINALIZADO" -> Color(0xFF80CBC4)
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
                color = Color(0xFF2E2E2E),
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
                color = Color(0xFF242424),
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 1.dp,
                color = Color(0xFF333333),
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
                color = Color(0xFF151515),
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

/**
 * Clasifica la actividad con los mismos criterios que el telefono.
 *
 * Antes cada dispositivo decidia por su cuenta y llegaban a conclusiones
 * distintas ante el mismo esfuerzo: el reloj promediaba la cadencia y el pulso
 * sobre toda la sesion, asi que despues de caminar un rato seguia diciendo
 * "Caminando" aunque la persona ya estuviera trotando, mientras el telefono
 * -que mira los ultimos segundos- ya lo habia detectado.
 *
 * Ahora ambos usan la cadencia reciente, el pulso actual y los mismos umbrales,
 * de modo que las dos pantallas coinciden. Cualquier cambio aqui debe hacerse
 * tambien en `detectarActividadHome` del telefono.
 *
 * [cadenciaReciente] vale null mientras no haya suficiente historia; en ese caso
 * se recurre a la acumulada.
 */
fun evaluarRitmo(
    bpmPromedio: Int,
    bpmActual: Int,
    aceleracionPromedio: Float,
    pasos: Int,
    segundos: Int,
    cadenciaReciente: Float? = null,
    pasosRecientes: Int? = null,
    pace: Float = 0f
): String {
    val cadenciaAcumulada = PrecisionWearUtils.calcularCadencia(
        pasos = pasos,
        segundos = segundos
    )

    val cadencia = cadenciaReciente ?: cadenciaAcumulada

    // Se prefiere el pulso actual sobre el promedio: el promedio arrastra todo
    // lo anterior y tarda en reflejar que el esfuerzo cambio.
    val pulso = if (bpmActual > 0) bpmActual else bpmPromedio

    if (bpmPromedio == 0 && bpmActual == 0) return "Analizando ritmo"
    if (segundos < 8) return "Preparando sensores"

    val sinMovimientoReciente = when (pasosRecientes) {
        null -> pasos == 0
        else -> pasosRecientes == 0
    }

    if (sinMovimientoReciente) return "Reposo"

    if (cadenciaReciente != null && cadenciaReciente < 15f) return "Casi detenido"

    if (cadencia > 135f || pulso > 150 ||
        (pace > 0f && pace <= 6.5f && pulso >= 135)
    ) {
        return "Corriendo"
    }

    if (cadencia in 86f..135f || pulso in 125..150 ||
        (pace > 6.5f && pace <= 10f && pulso >= 115)
    ) {
        return "Trotando"
    }

    if (cadencia in 1f..85f) return "Caminando"

    return "Analizando ritmo"
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
        "Corriendo" -> Color(0xFFFFB74D)
        "Trotando" -> Color(0xFF26A69A)
        "Caminando" -> Color(0xFF4DB6AC)
        "Reposo" -> Color(0xFFFFB74D)
        "Preparando sensores" -> Color(0xFF80CBC4)
        "Entrenamiento pausado" -> Color(0xFFFFB74D)
        "Entrenamiento finalizado" -> Color(0xFF80CBC4)
        "En espera" -> Color(0xFF80CBC4)
        else -> Color(0xFF80CBC4)
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
    }.asPutDataRequest()

    request.setUrgent()

    Wearable.getDataClient(context).putDataItem(request)
}

fun enviarComandoEntrenamiento(
    context: Context,
    accion: String,
    origen: String
) {
    val request = PutDataMapRequest.create(PATH_CONTROL_ENTRENAMIENTO).apply {
        dataMap.putString("accion", accion)
        dataMap.putString("origen", origen)
        dataMap.putLong("timestamp", System.currentTimeMillis())
    }.asPutDataRequest()

    request.setUrgent()

    Wearable.getDataClient(context).putDataItem(request)
}

/**
 * Variacion maxima creible del pulso, en latidos por segundo. Un salto mayor
 * entre dos lecturas es casi siempre un artefacto del sensor optico.
 */
private const val MAXIMO_CAMBIO_BPM_POR_SEGUNDO = 8.0

/**
 * Lecturas sobre las que se calcula la mediana. Se mantiene corto: Health
 * Services ya descarta las malas y una ventana amplia solo agregaria retraso.
 */
private const val VENTANA_MEDIANA_BPM = 3

/**
 * Rechazos consecutivos tolerados antes de aceptar la lectura.
 * Evita quedarse anclado a un valor equivocado si el pulso cambio de verdad.
 */
private const val MAXIMOS_RECHAZOS_SEGUIDOS = 4

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

        // Mismo techo que usa el telefono, que a su vez acompaña al rango con el
        // que se entreno la red (hasta 30 min/km). Con el limite anterior de 25
        // una caminata lenta quedaba descartada.
        if (pace > 30f) return 0f

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

}

@Composable
fun FondoReloj() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF0D2E2C),
                    Color.Black
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
            color = Color.White,
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