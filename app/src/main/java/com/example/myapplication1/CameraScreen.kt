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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Intentos totales del analisis de comida (el original mas dos reintentos).
 *
 * En pruebas reales, el servicio de Gemini tardo dos solicitudes seguidas en
 * despejarse durante un pico de saturacion. Con espera de por medio entre cada
 * intento (ver [ESPERA_ENTRE_INTENTOS_MS]), tres intentos cubren ese caso sin
 * alargar demasiado la espera percibida por el usuario.
 */
private const val MAXIMOS_INTENTOS_ANALISIS = 3

/** Espera base entre reintentos; crece con cada intento (2s, luego 4s). */
private const val ESPERA_ENTRE_INTENTOS_MS = 2000L

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

    // Los datos corporales alimentan la tarjeta de recomendacion. Si el usuario
    // no los ha llenado, el analisis funciona igual pero sin personalizar.
    val perfilFisico by PerfilStore.fisico.collectAsState()
    val metas = remember(perfilFisico) { perfilFisico.calcularMetas() }

    // El analisis vive en un almacen compartido, no en esta pantalla: al cambiar
    // de pestana la barra inferior destruye la pantalla y se perdia el resultado
    // que aun no se habia guardado. Ver AnalisisComidaStore.
    val analisis by AnalisisComidaStore.estado.collectAsState()

    val imageUri = analisis.imagenUri?.let(Uri::parse)
    val imageBase64 = analisis.imagenBase64
    val resultado = analisis.resultado
    val preparandoImagen = analisis.preparandoImagen
    val cargando = analisis.analizando
    val guardando = analisis.guardando
    val guardadoOk = analisis.guardadoOk
    val error = analisis.error

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
            AnalisisComidaStore.actualizar {
                it.copy(error = "No se pudo guardar: no hay una sesión de usuario activa.")
            }
            return
        }

        AnalisisComidaStore.actualizar {
            it.copy(guardando = true, guardadoOk = false, error = null)
        }

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
                    if (response.isSuccessful) {
                        AnalisisComidaStore.actualizar {
                            it.copy(guardando = false, guardadoOk = true)
                        }
                        cargarResumenDelDia()
                        cargarComidas()
                    } else {
                        AnalisisComidaStore.actualizar {
                            it.copy(
                                guardando = false,
                                error = "No se pudo guardar la comida (código ${response.code()})."
                            )
                        }
                    }
                }

                override fun onFailure(call: Call<MealResponse>, t: Throwable) {
                    AnalisisComidaStore.actualizar {
                        it.copy(
                            guardando = false,
                            error = "Error de conexión al guardar: ${t.message}"
                        )
                    }
                }
            })
    }

    // El decodificado y la compresion de la fotografia son operaciones costosas,
    // por eso se ejecutan fuera del hilo principal para no congelar la interfaz.
    fun prepararImagen(uri: Uri) {
        AnalisisComidaStore.actualizar {
            it.copy(
                imagenUri = uri.toString(),
                imagenBase64 = null,
                resultado = null,
                error = null,
                guardadoOk = false,
                preparandoImagen = true
            )
        }

        // Mismo alcance de larga vida que los reintentos: si el usuario cambia
        // de pestana mientras se comprime la fotografia, el trabajo termina y
        // encuentra la imagen lista al volver.
        AnalisisComidaStore.alcance.launch {
            val base64 = withContext(Dispatchers.Default) {
                runCatching {
                    val stream: InputStream? = context.contentResolver.openInputStream(uri)
                    val bytes = stream.use { it?.readBytes() } ?: return@runCatching null
                    bitmapAJpegBase64(bytes)
                }.getOrNull()
            }

            AnalisisComidaStore.actualizar {
                if (base64 == null) {
                    it.copy(
                        preparandoImagen = false,
                        error = "No se pudo procesar la imagen seleccionada.",
                        imagenUri = null
                    )
                } else {
                    it.copy(
                        preparandoImagen = false,
                        imagenBase64 = base64,
                        tipoImagen = "image/jpeg"
                    )
                }
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
     * El backend reenvia el codigo de estado que devuelve Gemini, y ese codigo
     * distingue dos situaciones muy distintas que no deben tratarse igual:
     *
     * - **503 (servidor saturado):** el propio servicio de Gemini esta con
     *   demanda alta en ese instante. Es momentaneo, y reintentar con una breve
     *   espera de por medio suele resolverlo.
     * - **429 (cuota agotada):** la cuenta ya uso su limite de solicitudes.
     *   Reintentar de inmediato no sirve de nada porque el limite no se libera
     *   en segundos, asi que en este caso se avisa directamente en lugar de
     *   reintentar a ciegas.
     *
     * El primer intento no espera; los reintentos si, porque la saturacion de
     * Gemini rara vez se resuelve en el mismo instante en que ocurre.
     */
    fun analizarImagen(intento: Int = 1) {
        val base64 = imageBase64 ?: return

        AnalisisComidaStore.actualizar {
            it.copy(
                analizando = true,
                error = null,
                resultado = null,
                guardadoOk = false,
                reintentando = intento > 1
            )
        }

        // Se envia el perfil solo cuando esta completo: mandarlo a medias haria
        // que la IA redactara consejos basados en ceros.
        val perfilParaIA = metas?.let { meta ->
            PerfilNutricional(
                edad = perfilFisico.edad,
                sexo = perfilFisico.sexo.etiqueta,
                estaturaCm = perfilFisico.estaturaCm,
                pesoKg = perfilFisico.pesoKg,
                nivelActividad = perfilFisico.nivelActividad.etiqueta,
                objetivo = perfilFisico.objetivo.etiqueta,
                caloriasMeta = meta.caloriasMeta,
                proteinaMeta = meta.proteinaMeta,
                carbosMeta = meta.carbosMeta,
                grasasMeta = meta.grasasMeta,
                caloriasConsumidas = resumenDelDia?.totalCalories ?: 0,
                proteinaConsumida = resumenDelDia?.totalProtein ?: 0,
                carbosConsumidos = resumenDelDia?.totalCarbs ?: 0,
                grasasConsumidas = resumenDelDia?.totalFats ?: 0
            )
        }

        RetrofitClient.instance
            .analyzeFood(
                FoodAnalysisRequest(
                    imageBase64 = base64,
                    mimeType = analisis.tipoImagen,
                    perfil = perfilParaIA
                )
            )
            .enqueue(object : Callback<FoodAnalysisResponse> {
                override fun onResponse(
                    call: Call<FoodAnalysisResponse>,
                    response: Response<FoodAnalysisResponse>
                ) {
                    val cuerpo = response.body()

                    if (response.code() == 429) {
                        AnalisisComidaStore.actualizar {
                            it.copy(
                                analizando = false,
                                reintentando = false,
                                error = "Se alcanzó el límite de análisis de IA por ahora. " +
                                    "Espera unos minutos e intenta de nuevo."
                            )
                        }
                        return
                    }

                    // Errores de saturacion del servicio: se reintenta con una
                    // breve espera para no repetir la misma foto instantaneamente
                    // contra un servicio que sigue saturado en ese milisegundo.
                    if (response.code() >= 500 && intento < MAXIMOS_INTENTOS_ANALISIS) {
                        AnalisisComidaStore.actualizar { it.copy(reintentando = true) }
                        AnalisisComidaStore.alcance.launch {
                            delay(ESPERA_ENTRE_INTENTOS_MS * intento)
                            analizarImagen(intento + 1)
                        }
                        return
                    }

                    val mensajeError = when {
                        response.code() >= 500 ->
                            "El servicio de análisis está saturado en este momento. " +
                                "Intenta de nuevo en unos segundos."

                        !response.isSuccessful || cuerpo == null ->
                            "El servidor no pudo analizar la imagen (código ${response.code()})."

                        !cuerpo.isFood ->
                            cuerpo.observation.ifBlank { "No se detectó comida en la imagen." }

                        else -> null
                    }

                    AnalisisComidaStore.actualizar {
                        it.copy(
                            analizando = false,
                            reintentando = false,
                            error = mensajeError,
                            resultado = if (mensajeError == null) cuerpo else null
                        )
                    }
                }

                override fun onFailure(call: Call<FoodAnalysisResponse>, t: Throwable) {
                    if (intento < MAXIMOS_INTENTOS_ANALISIS) {
                        AnalisisComidaStore.actualizar { it.copy(reintentando = true) }
                        AnalisisComidaStore.alcance.launch {
                            delay(ESPERA_ENTRE_INTENTOS_MS * intento)
                            analizarImagen(intento + 1)
                        }
                        return
                    }

                    AnalisisComidaStore.actualizar {
                        it.copy(
                            analizando = false,
                            reintentando = false,
                            error = if (t is java.net.SocketTimeoutException) {
                                "El servidor tardó demasiado en responder. Vuelve a intentarlo."
                            } else {
                                "No se pudo conectar con el servidor. Revisa tu internet e intenta de nuevo."
                            }
                        )
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
                            Text(
                                text = if (metas != null) {
                                    "${resumen.totalCalories} / ${metas.caloriasMeta} kcal"
                                } else {
                                    "${resumen.totalCalories} kcal"
                                },
                                fontSize = dimens.bodyFontSize,
                                fontWeight = FontWeight.Bold,
                                color = TealPrimary
                            )
                            Text("P: ${resumen.totalProtein}g", fontSize = dimens.labelFontSize, color = uiColors.textSecondary)
                            Text("C: ${resumen.totalCarbs}g", fontSize = dimens.labelFontSize, color = uiColors.textSecondary)
                            Text("G: ${resumen.totalFats}g", fontSize = dimens.labelFontSize, color = uiColors.textSecondary)
                        }

                        // Con las metas ya calculadas, el resumen deja de ser un
                        // dato suelto y pasa a indicar cuanto falta del dia.
                        metas?.let { meta ->
                            val restante = meta.caloriasMeta - resumen.totalCalories
                            val avance = porcentajeDeMeta(resumen.totalCalories, meta.caloriasMeta)

                            Spacer(modifier = Modifier.height(10.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(uiColors.border.copy(alpha = 0.35f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth((avance / 100f).coerceIn(0f, 1f))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (avance > 100) uiColors.warning else TealPrimary)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = if (restante >= 0) {
                                    "Te quedan $restante kcal para hoy"
                                } else {
                                    "Has superado tu meta por ${-restante} kcal"
                                },
                                fontSize = dimens.labelFontSize,
                                color = if (restante >= 0) uiColors.textSecondary else uiColors.warning
                            )
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
                            analisis.reintentando -> "Reintentando..."
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

                        SeccionRecomendacion(
                            res = res,
                            metas = metas,
                            resumen = resumenDelDia,
                            uiColors = uiColors,
                            dimens = dimens,
                            onCompletarDatos = { navController.navigate("datos_corporales") }
                        )

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

/**
 * Bloque "Recomendación para ti": traduce el analisis nutricional al contexto
 * del usuario concreto.
 *
 * Las cifras y los porcentajes salen de las metas calculadas con formula, no
 * del modelo de IA. Del modelo viene unicamente el consejo escrito, para que un
 * error suyo nunca se convierta en un numero equivocado en pantalla.
 */
@Composable
private fun SeccionRecomendacion(
    res: FoodAnalysisResponse,
    metas: MetasNutricionales?,
    resumen: MealSummaryResponse?,
    uiColors: AppUiColors,
    dimens: ResponsiveDimens,
    onCompletarDatos: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TealPrimary.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .border(1.dp, TealPrimary.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = TealPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Recomendación para ti",
                fontSize = dimens.bodyFontSize,
                fontWeight = FontWeight.Bold,
                color = TealPrimary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (metas == null) {
            Text(
                text = "Agrega tu estatura, peso y edad para que ALYRA calcule si esta " +
                        "porción encaja en tu día.",
                fontSize = dimens.labelFontSize,
                color = uiColors.textSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onCompletarDatos,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, TealPrimary)
            ) {
                Text(
                    text = "Completar mis datos",
                    color = TealPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = dimens.labelFontSize
                )
            }
            return@Column
        }

        val consumidas = resumen?.totalCalories ?: 0
        val totalConEsta = consumidas + res.estimatedCalories
        val restantes = metas.caloriasMeta - totalConEsta
        val porcentajeComida = porcentajeDeMeta(res.estimatedCalories, metas.caloriasMeta)

        Text(
            text = "Esta porción representa el $porcentajeComida% de tus " +
                    "${metas.caloriasMeta} kcal del día.",
            fontSize = dimens.bodyFontSize,
            color = uiColors.textPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        BarraProgreso(
            etiqueta = "Calorías de hoy",
            valor = totalConEsta,
            meta = metas.caloriasMeta,
            unidad = "kcal",
            uiColors = uiColors,
            dimens = dimens
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (restantes >= 0) {
                "Después de esta comida te quedan $restantes kcal para hoy."
            } else {
                "Con esta comida superarías tu meta por ${-restantes} kcal."
            },
            fontSize = dimens.labelFontSize,
            fontWeight = FontWeight.Medium,
            color = if (restantes >= 0) uiColors.textSecondary else uiColors.warning
        )

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = TealPrimary.copy(alpha = 0.25f), thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Cómo va tu día en macronutrientes",
            fontSize = dimens.labelFontSize,
            fontWeight = FontWeight.Bold,
            color = uiColors.textSecondary
        )

        Spacer(modifier = Modifier.height(10.dp))

        BarraProgreso(
            etiqueta = "Proteína",
            valor = (resumen?.totalProtein ?: 0) + res.protein,
            meta = metas.proteinaMeta,
            unidad = "g",
            uiColors = uiColors,
            dimens = dimens
        )
        Spacer(modifier = Modifier.height(8.dp))
        BarraProgreso(
            etiqueta = "Carbohidratos",
            valor = (resumen?.totalCarbs ?: 0) + res.carbs,
            meta = metas.carbosMeta,
            unidad = "g",
            uiColors = uiColors,
            dimens = dimens
        )
        Spacer(modifier = Modifier.height(8.dp))
        BarraProgreso(
            etiqueta = "Grasas",
            valor = (resumen?.totalFats ?: 0) + res.fats,
            meta = metas.grasasMeta,
            unidad = "g",
            uiColors = uiColors,
            dimens = dimens
        )

        if (res.porcionSugerida.isNotBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(uiColors.card, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Porción sugerida para ti",
                        fontSize = dimens.labelFontSize,
                        color = uiColors.textSecondary
                    )
                    Text(
                        text = res.porcionSugerida,
                        fontSize = dimens.bodyFontSize,
                        fontWeight = FontWeight.Bold,
                        color = uiColors.textPrimary
                    )
                }
            }
        }

        if (res.recomendacion.isNotBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = res.recomendacion,
                fontSize = dimens.bodyFontSize,
                color = uiColors.textPrimary
            )
        }
    }
}

/**
 * Barra de avance hacia una meta diaria. Cuando el valor la rebasa, la barra se
 * llena por completo y cambia de color en lugar de desbordarse.
 */
@Composable
private fun BarraProgreso(
    etiqueta: String,
    valor: Int,
    meta: Int,
    unidad: String,
    uiColors: AppUiColors,
    dimens: ResponsiveDimens
) {
    val porcentaje = porcentajeDeMeta(valor, meta)
    val excedido = porcentaje > 100
    val colorBarra = if (excedido) uiColors.warning else TealPrimary

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = etiqueta, fontSize = dimens.labelFontSize, color = uiColors.textSecondary)
            Text(
                text = "$valor / $meta $unidad",
                fontSize = dimens.labelFontSize,
                fontWeight = FontWeight.Bold,
                color = if (excedido) uiColors.warning else uiColors.textPrimary
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(uiColors.border.copy(alpha = 0.35f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((porcentaje / 100f).coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(colorBarra)
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
