package com.example.myapplication1

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Intentos totales del analisis de comida (el original mas un reintento).
 * Basta con uno: el primer intento fallido ya deja el servidor encendido.
 */
private const val MAXIMOS_INTENTOS_ANALISIS = 2

private val TealPrimary = Color(0xFF26A69A)
private val TealMedium  = Color(0xFF4DB6AC)

// Escala la imagen respetando su forma original (nunca la deforma a un cuadrado)
// y solo la reduce si excede maxDim, para no perder calidad innecesariamente.
private fun bitmapAJpegBase64(
    bytes: ByteArray,
    maxDim: Int = 1280,
    calidad: Int = 85
): String {
    val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val anchoOriginal = original.width
    val altoOriginal = original.height

    val escala = minOf(
        maxDim.toFloat() / anchoOriginal,
        maxDim.toFloat() / altoOriginal,
        1f // nunca agrandar, solo reducir si hace falta
    )

    val bitmapFinal = if (escala < 1f) {
        val anchoNuevo = (anchoOriginal * escala).toInt().coerceAtLeast(1)
        val altoNuevo = (altoOriginal * escala).toInt().coerceAtLeast(1)
        android.graphics.Bitmap.createScaledBitmap(original, anchoNuevo, altoNuevo, true)
    } else {
        original
    }

    val stream = ByteArrayOutputStream()
    bitmapFinal.compress(android.graphics.Bitmap.CompressFormat.JPEG, calidad, stream)
    return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
}

@Composable
fun CameraScreen(navController: NavController) {
    val context = LocalContext.current

    // La sesion es la unica fuente del identificador de usuario, igual que en
    // AgendaScreen y en el guardado automatico de entrenamientos.
    val sessionManager = remember { SessionManager(context) }
    val userId = sessionManager.getUserId()

    val settings by AppSettingsStore.settings.collectAsState()
    val uiColors = appUiColors(settings.temaOscuro)
    val dimens = rememberResponsiveDimens()
    val scope = rememberCoroutineScope()

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageBase64 by remember { mutableStateOf<String?>(null) }
    var imageMediaType by remember { mutableStateOf("image/jpeg") }
    var resultado by remember { mutableStateOf<FoodAnalysisResponse?>(null) }
    var preparandoImagen by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(false) }
    var reintentando by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var guardadoOk by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var resumenDelDia by remember { mutableStateOf<MealSummaryResponse?>(null) }

    var pestanaSeleccionada by remember { mutableIntStateOf(0) }
    var comidas by remember { mutableStateOf<List<MealItem>>(emptyList()) }
    var comidaParaEliminar by remember { mutableStateOf<MealItem?>(null) }
    var mensajeHistorial by remember { mutableStateOf("Cargando historial...") }

    fun fechaHoy(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    fun cargarResumenDelDia() {
        val uid = userId ?: return

        RetrofitClient.instance.getMealSummary(uid, fechaHoy())
            .enqueue(object : Callback<MealSummaryResponse> {
                override fun onResponse(
                    call: Call<MealSummaryResponse>,
                    response: Response<MealSummaryResponse>
                ) {
                    if (response.isSuccessful) {
                        resumenDelDia = response.body()
                    }
                }

                // Si falla el resumen no interrumpimos el resto de la pantalla,
                // simplemente no se muestra el total del dia.
                override fun onFailure(call: Call<MealSummaryResponse>, t: Throwable) = Unit
            })
    }

    fun cargarComidas() {
        val uid = userId

        if (uid.isNullOrBlank()) {
            comidas = emptyList()
            mensajeHistorial = "No hay sesión iniciada"
            return
        }

        RetrofitClient.instance.getMeals(uid)
            .enqueue(object : Callback<List<MealItem>> {
                override fun onResponse(
                    call: Call<List<MealItem>>,
                    response: Response<List<MealItem>>
                ) {
                    if (response.isSuccessful) {
                        comidas = response.body().orEmpty()

                        mensajeHistorial = if (comidas.isEmpty()) {
                            "Todavía no has registrado ninguna comida."
                        } else {
                            "Comidas registradas: ${comidas.size}"
                        }
                    } else {
                        mensajeHistorial = "No se pudo cargar el historial (código ${response.code()})."
                    }
                }

                override fun onFailure(call: Call<List<MealItem>>, t: Throwable) {
                    mensajeHistorial = "Fallo de conexión: ${t.message}"
                }
            })
    }

    fun eliminarComida(comida: MealItem) {
        val idComida = comida._id

        if (idComida.isNullOrBlank()) {
            comidaParaEliminar = null
            return
        }

        RetrofitClient.instance.deleteMeal(idComida)
            .enqueue(object : Callback<DeleteResponse> {
                override fun onResponse(
                    call: Call<DeleteResponse>,
                    response: Response<DeleteResponse>
                ) {
                    comidaParaEliminar = null

                    if (response.isSuccessful) {
                        comidas = comidas.filter { it._id != idComida }
                        cargarResumenDelDia()
                    }
                }

                override fun onFailure(call: Call<DeleteResponse>, t: Throwable) {
                    comidaParaEliminar = null
                }
            })
    }

    LaunchedEffect(userId) {
        cargarResumenDelDia()
        cargarComidas()
    }

    fun guardarComida() {
        val res = resultado ?: return
        val uid = userId

        if (uid.isNullOrBlank()) {
            error = "No se pudo guardar: no hay una sesión de usuario activa."
            return
        }

        guardando = true
        guardadoOk = false
        error = null

        val comida = MealRequest(
            userId = uid,
            foodName = res.foodName,
            category = res.category,
            portion = res.portion,
            estimatedCalories = res.estimatedCalories,
            calorieRange = res.calorieRange,
            protein = res.protein,
            carbs = res.carbs,
            fats = res.fats,
            confidence = res.confidence,
            observation = res.observation,
            date = fechaHoy()
        )

        RetrofitClient.instance.saveMeal(comida)
            .enqueue(object : Callback<MealResponse> {
                override fun onResponse(
                    call: Call<MealResponse>,
                    response: Response<MealResponse>
                ) {
                    guardando = false

                    if (response.isSuccessful) {
                        guardadoOk = true
                        cargarResumenDelDia()
                        cargarComidas()
                    } else {
                        error = "No se pudo guardar la comida (código ${response.code()})."
                    }
                }

                override fun onFailure(call: Call<MealResponse>, t: Throwable) {
                    guardando = false
                    error = "Error de conexión al guardar: ${t.message}"
                }
            })
    }

    // El decodificado y la compresion de la fotografia son operaciones costosas,
    // por eso se ejecutan fuera del hilo principal para no congelar la interfaz.
    fun prepararImagen(uri: Uri) {
        imageUri = uri
        resultado = null
        error = null
        guardadoOk = false
        preparandoImagen = true

        scope.launch {
            val base64 = withContext(Dispatchers.Default) {
                runCatching {
                    val stream: InputStream? = context.contentResolver.openInputStream(uri)
                    val bytes = stream.use { it?.readBytes() } ?: return@runCatching null
                    bitmapAJpegBase64(bytes)
                }.getOrNull()
            }

            preparandoImagen = false

            if (base64 == null) {
                error = "No se pudo procesar la imagen seleccionada."
                imageUri = null
            } else {
                imageBase64 = base64
                imageMediaType = "image/jpeg"
            }
        }
    }

    val galeriaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) prepararImagen(uri)
    }

    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }

    val camaraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { exito: Boolean ->
        val uri = cameraTempUri
        if (exito && uri != null) prepararImagen(uri)
    }

    fun lanzarCamara() {
        val archivo = java.io.File(
            context.cacheDir,
            "foto_comida_${System.currentTimeMillis()}.jpg"
        )
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            archivo
        )
        cameraTempUri = uri
        camaraLauncher.launch(uri)
    }

    /**
     * Envia la fotografia al backend para su analisis.
     *
     * Se reintenta una vez de forma automatica ante fallos de red o de tiempo de
     * espera. La causa habitual de ese primer fallo es que el servidor estaba
     * suspendido por inactividad: ese intento inicial lo despierta, y el segundo
     * ya encuentra el servicio disponible. Reintentar evita que el usuario vea
     * un error por algo que se resuelve solo en unos segundos.
     */
    fun analizarImagen(intento: Int = 1) {
        val base64 = imageBase64 ?: return

        cargando = true
        error = null
        resultado = null
        guardadoOk = false
        reintentando = intento > 1

        RetrofitClient.instance
            .analyzeFood(FoodAnalysisRequest(imageBase64 = base64, mimeType = imageMediaType))
            .enqueue(object : Callback<FoodAnalysisResponse> {
                override fun onResponse(
                    call: Call<FoodAnalysisResponse>,
                    response: Response<FoodAnalysisResponse>
                ) {
                    val cuerpo = response.body()

                    // Los errores 5xx suelen indicar que el servidor apenas esta
                    // arrancando, por eso tambien se reintentan.
                    if (response.code() >= 500 && intento < MAXIMOS_INTENTOS_ANALISIS) {
                        analizarImagen(intento + 1)
                        return
                    }

                    cargando = false
                    reintentando = false

                    when {
                        !response.isSuccessful || cuerpo == null ->
                            error = "El servidor no pudo analizar la imagen (código ${response.code()})."

                        !cuerpo.isFood ->
                            error = cuerpo.observation.ifBlank {
                                "No se detectó comida en la imagen."
                            }

                        else -> resultado = cuerpo
                    }
                }

                override fun onFailure(call: Call<FoodAnalysisResponse>, t: Throwable) {
                    if (intento < MAXIMOS_INTENTOS_ANALISIS) {
                        analizarImagen(intento + 1)
                        return
                    }

                    cargando = false
                    reintentando = false

                    error = if (t is java.net.SocketTimeoutException) {
                        "El servidor tardó demasiado en responder. Vuelve a intentarlo."
                    } else {
                        "No se pudo conectar con el servidor. Revisa tu internet e intenta de nuevo."
                    }
                }
            })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(uiColors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.horizontalPadding)
                .padding(top = 16.dp, bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EntrenamientoActivoBanner(
                navController = navController,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = "Control de comidas",
                fontSize = dimens.titleFontSize,
                fontWeight = FontWeight.Bold,
                color = uiColors.textPrimary,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = if (pestanaSeleccionada == 0) {
                    "Toma o sube una foto de tu comida"
                } else {
                    "Todo lo que has registrado"
                },
                fontSize = dimens.bodyFontSize,
                color = uiColors.textSecondary,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            TabRow(
                selectedTabIndex = pestanaSeleccionada,
                containerColor = uiColors.cardSecondary,
                contentColor = TealPrimary,
                modifier = Modifier.clip(RoundedCornerShape(14.dp))
            ) {
                Tab(
                    selected = pestanaSeleccionada == 0,
                    onClick = { pestanaSeleccionada = 0 },
                    selectedContentColor = TealPrimary,
                    unselectedContentColor = uiColors.textMuted,
                    text = { Text("Analizar", fontWeight = FontWeight.Bold) }
                )

                Tab(
                    selected = pestanaSeleccionada == 1,
                    onClick = {
                        pestanaSeleccionada = 1
                        cargarComidas()
                    },
                    selectedContentColor = TealPrimary,
                    unselectedContentColor = uiColors.textMuted,
                    text = { Text("Historial", fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // El resumen de hoy solo acompana al analizador: en el historial los
            // totales ya aparecen agrupados por cada fecha.
            resumenDelDia?.let { resumen ->
                if (pestanaSeleccionada == 0 && resumen.cantidadComidas > 0) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(uiColors.cardSecondary, RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "Hoy · ${resumen.cantidadComidas} comida${if (resumen.cantidadComidas == 1) "" else "s"} registrada${if (resumen.cantidadComidas == 1) "" else "s"}",
                            fontSize = dimens.labelFontSize,
                            fontWeight = FontWeight.Bold,
                            color = uiColors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${resumen.totalCalories} kcal", fontSize = dimens.bodyFontSize, fontWeight = FontWeight.Bold, color = TealPrimary)
                            Text("P: ${resumen.totalProtein}g", fontSize = dimens.labelFontSize, color = uiColors.textSecondary)
                            Text("C: ${resumen.totalCarbs}g", fontSize = dimens.labelFontSize, color = uiColors.textSecondary)
                            Text("G: ${resumen.totalFats}g", fontSize = dimens.labelFontSize, color = uiColors.textSecondary)
                        }
                    }
                }
            }

            if (pestanaSeleccionada == 0) {
                Spacer(modifier = Modifier.height(20.dp))

                if (imageUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(uiColors.cardSecondary)
                            .border(2.dp, TealPrimary, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            painter = rememberAsyncImagePainter(imageUri),
                            contentDescription = "Foto de comida",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(uiColors.cardSecondary, RoundedCornerShape(20.dp))
                            .border(2.dp, uiColors.border, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = uiColors.textMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Sin imagen",
                                fontSize = dimens.labelFontSize,
                                color = uiColors.textMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { lanzarCamara() },
                        enabled = !preparandoImagen && !cargando,
                        modifier = Modifier.weight(1f).height(dimens.buttonHeight),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, TealPrimary)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = TealPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cámara", color = TealPrimary, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { galeriaLauncher.launch("image/*") },
                        enabled = !preparandoImagen && !cargando,
                        modifier = Modifier.weight(1f).height(dimens.buttonHeight),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, TealPrimary)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = TealPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Galería", color = TealPrimary, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { analizarImagen() },
                    enabled = imageBase64 != null && !cargando && !preparandoImagen,
                    modifier = Modifier.fillMaxWidth().height(dimens.buttonHeight),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TealPrimary,
                        contentColor = Color.White,
                        disabledContainerColor = uiColors.border
                    )
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            preparandoImagen -> "Preparando imagen..."
                            cargando -> "Analizando..."
                            else -> "Analizar con IA"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = dimens.buttonFontSize
                    )
                }

                if (cargando || preparandoImagen) {
                    Spacer(modifier = Modifier.height(20.dp))
                    CircularProgressIndicator(color = TealPrimary)
                }

                error?.let {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(uiColors.dangerButton.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Text(text = it, color = uiColors.dangerButton, fontSize = dimens.bodyFontSize)
                    }
                }

                resultado?.let { res ->
                    Spacer(modifier = Modifier.height(20.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(uiColors.card, RoundedCornerShape(20.dp))
                            .border(1.dp, TealPrimary, RoundedCornerShape(20.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Resultado nutricional",
                            fontSize = dimens.titleFontSize,
                            fontWeight = FontWeight.Bold,
                            color = TealPrimary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Nombre del alimento (título propio, con espacio de sobra para envolver)
                        Text(
                            text = res.foodName,
                            fontSize = dimens.bodyFontSize,
                            fontWeight = FontWeight.Bold,
                            color = uiColors.textPrimary
                        )

                        if (res.category.isNotBlank() || res.portion.isNotBlank()) {
                            Text(
                                text = listOfNotNull(
                                    res.category.takeIf { it.isNotBlank() },
                                    res.portion.takeIf { it.isNotBlank() }
                                ).joinToString(" · "),
                                fontSize = dimens.labelFontSize,
                                color = uiColors.textSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = uiColors.border, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Calorías: el rango es el dato principal, la cifra puntual va como "~aprox."
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "≈ ${res.estimatedCalories} kcal",
                                fontSize = dimens.titleFontSize,
                                fontWeight = FontWeight.Bold,
                                color = TealPrimary
                            )
                            // El rango solo aporta informacion cuando hay calorias
                            // que estimar; en alimentos sin aporte se omite.
                            if (res.estimatedCalories > 0 && res.calorieRange.isNotBlank()) {
                                Text(
                                    text = "Rango estimado: ${res.calorieRange}",
                                    fontSize = dimens.labelFontSize,
                                    color = uiColors.textSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = uiColors.border, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Macronutrientes: tres columnas iguales
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            MacroColumn(label = "Proteína", valor = res.protein, color = uiColors.textPrimary)
                            MacroColumn(label = "Carbos", valor = res.carbs, color = uiColors.textPrimary)
                            MacroColumn(label = "Grasas", valor = res.fats, color = uiColors.textPrimary)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = uiColors.border, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        if (res.confidence.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Confianza",
                                    fontSize = dimens.bodyFontSize,
                                    fontWeight = FontWeight.Bold,
                                    color = uiColors.textPrimary
                                )
                                Text(
                                    text = res.confidence.replaceFirstChar { it.uppercase() },
                                    fontSize = dimens.bodyFontSize,
                                    color = TealMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (res.observation.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = res.observation,
                                fontSize = dimens.labelFontSize,
                                color = uiColors.textSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { guardarComida() },
                            enabled = !guardando && !guardadoOk && !userId.isNullOrBlank(),
                            modifier = Modifier.fillMaxWidth().height(dimens.buttonHeight),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TealMedium,
                                contentColor = Color.White,
                                disabledContainerColor = uiColors.border
                            )
                        ) {
                            Text(
                                text = when {
                                    guardando -> "Guardando..."
                                    guardadoOk -> "Guardado ✓"
                                    else -> "Guardar en mi historial"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = dimens.buttonFontSize
                            )
                        }

                        if (guardadoOk) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Comida guardada en tu historial ✓",
                                fontSize = dimens.labelFontSize,
                                color = TealPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))

                if (comidas.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(uiColors.cardSecondary, RoundedCornerShape(16.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mensajeHistorial,
                            fontSize = dimens.bodyFontSize,
                            color = uiColors.textMuted
                        )
                    }
                } else {
                    comidas
                        .groupBy { it.date }
                        .toList()
                        .sortedByDescending { (fecha, _) -> fecha }
                        .forEach { (fecha, comidasDelDia) ->
                            DiaDeComidas(
                                fecha = fecha,
                                comidasDelDia = comidasDelDia,
                                uiColors = uiColors,
                                dimens = dimens,
                                onEliminar = { comidaParaEliminar = it }
                            )

                            Spacer(modifier = Modifier.height(18.dp))
                        }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = { cargarComidas() },
                    modifier = Modifier.fillMaxWidth().height(dimens.buttonHeight),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TealPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Text("Actualizar historial", fontWeight = FontWeight.Bold)
                }
            }
        }

        comidaParaEliminar?.let { comida ->
            AlertDialog(
                onDismissRequest = { comidaParaEliminar = null },
                containerColor = uiColors.card,
                title = {
                    Text(
                        text = "Eliminar comida",
                        color = uiColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "¿Seguro que quieres eliminar este registro?\n\n" +
                                "${comida.foodName}\n" +
                                "${comida.date}\n" +
                                "${comida.estimatedCalories} kcal",
                        color = uiColors.textSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = { eliminarComida(comida) }) {
                        Text(
                            text = "Eliminar",
                            color = uiColors.dangerButton,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { comidaParaEliminar = null }) {
                        Text(
                            text = "Cancelar",
                            color = uiColors.textSecondary
                        )
                    }
                }
            )
        }

        NavigationBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            containerColor = uiColors.bottomBar,
            contentColor = uiColors.bottomUnselected
        ) {
            NavigationBarItem(selected = false, onClick = { navController.irASeccion("home") }, icon = { Icon(Icons.Default.Home, contentDescription = null) }, label = { Text("Home") }, colors = bottomItemColors(uiColors))
            NavigationBarItem(selected = false, onClick = { navController.irASeccion("IA") }, icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) }, label = { Text("IA") }, colors = bottomItemColors(uiColors))
            NavigationBarItem(selected = true, onClick = { }, icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) }, label = { Text("Comida") }, colors = bottomItemColors(uiColors))
            NavigationBarItem(selected = false, onClick = { navController.irASeccion("agenda") }, icon = { Icon(Icons.Default.DateRange, contentDescription = null) }, label = { Text("Agenda") }, colors = bottomItemColors(uiColors))
        }
    }
}

/**
 * Agrupa en un bloque todas las comidas registradas en una misma fecha,
 * encabezadas por los totales de ese dia.
 */
@Composable
private fun DiaDeComidas(
    fecha: String,
    comidasDelDia: List<MealItem>,
    uiColors: AppUiColors,
    dimens: ResponsiveDimens,
    onEliminar: (MealItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = formatearFechaLarga(fecha),
            fontSize = dimens.bodyFontSize,
            fontWeight = FontWeight.Bold,
            color = uiColors.textPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(uiColors.cardSecondary, RoundedCornerShape(14.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${comidasDelDia.sumOf { it.estimatedCalories }} kcal",
                fontSize = dimens.bodyFontSize,
                fontWeight = FontWeight.Bold,
                color = TealPrimary
            )
            Text("P: ${comidasDelDia.sumOf { it.protein }}g", fontSize = dimens.labelFontSize, color = uiColors.textSecondary)
            Text("C: ${comidasDelDia.sumOf { it.carbs }}g", fontSize = dimens.labelFontSize, color = uiColors.textSecondary)
            Text("G: ${comidasDelDia.sumOf { it.fats }}g", fontSize = dimens.labelFontSize, color = uiColors.textSecondary)
        }

        comidasDelDia.forEach { comida ->
            Spacer(modifier = Modifier.height(10.dp))

            MealHistoryCard(
                comida = comida,
                uiColors = uiColors,
                dimens = dimens,
                onEliminar = { onEliminar(comida) }
            )
        }
    }
}

@Composable
private fun MealHistoryCard(
    comida: MealItem,
    uiColors: AppUiColors,
    dimens: ResponsiveDimens,
    onEliminar: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(uiColors.card, RoundedCornerShape(16.dp))
            .border(1.dp, uiColors.border, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = comida.foodName.ifBlank { "Alimento sin nombre" },
                    fontSize = dimens.bodyFontSize,
                    fontWeight = FontWeight.Bold,
                    color = uiColors.textPrimary
                )

                val subtitulo = listOfNotNull(
                    comida.category.takeIf { it.isNotBlank() },
                    comida.portion.takeIf { it.isNotBlank() }
                ).joinToString(" · ")

                if (subtitulo.isNotBlank()) {
                    Text(
                        text = subtitulo,
                        fontSize = dimens.labelFontSize,
                        color = uiColors.textSecondary
                    )
                }
            }

            Text(
                text = "${comida.estimatedCalories} kcal",
                fontSize = dimens.bodyFontSize,
                fontWeight = FontWeight.Bold,
                color = TealPrimary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = uiColors.border, thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MacroColumn(label = "Proteína", valor = comida.protein, color = uiColors.textPrimary)
            MacroColumn(label = "Carbos", valor = comida.carbs, color = uiColors.textPrimary)
            MacroColumn(label = "Grasas", valor = comida.fats, color = uiColors.textPrimary)
        }

        if (comida.estimatedCalories > 0 && comida.calorieRange.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Rango estimado: ${comida.calorieRange}",
                fontSize = dimens.labelFontSize,
                color = uiColors.textSecondary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = onEliminar,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(
                text = "Eliminar",
                color = uiColors.dangerButton,
                fontWeight = FontWeight.Bold,
                fontSize = dimens.labelFontSize
            )
        }
    }
}

@Composable
private fun MacroColumn(label: String, valor: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${valor}g",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = color.copy(alpha = 0.7f)
        )
    }
}
