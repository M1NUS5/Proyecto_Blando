package com.example.myapplication1

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

private val TealPrimary = Color(0xFF26A69A)
private val TealMedium  = Color(0xFF4DB6AC)

data class FoodAnalysisResult(
    val foodName: String,
    val category: String,
    val portion: String,
    val estimatedCalories: Int,
    val calorieRange: String,
    val protein: Int,
    val carbs: Int,
    val fats: Int,
    val confidence: String,
    val observation: String
)

data class ResumenDelDia(
    val totalCalories: Int,
    val totalProtein: Int,
    val totalCarbs: Int,
    val totalFats: Int,
    val cantidadComidas: Int
)

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
fun CameraScreen(
    navController: NavController,
    // TODO: reemplaza este valor por tu fuente real de sesión
    // (ej. UserSession.userId, un DataStore, o un argumento de tu grafo de navegación).
    userId: String? = null
) {
    val context = LocalContext.current
    val settings by AppSettingsStore.settings.collectAsState()
    val uiColors = appUiColors(settings.temaOscuro)
    val dimens = rememberResponsiveDimens()
    val scope = rememberCoroutineScope()

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageBase64 by remember { mutableStateOf<String?>(null) }
    var imageMediaType by remember { mutableStateOf("image/jpeg") }
    var resultado by remember { mutableStateOf<FoodAnalysisResult?>(null) }
    var cargando by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var guardadoOk by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var resumenDelDia by remember { mutableStateOf<ResumenDelDia?>(null) }

    val BASE_URL = "https://entrenador-ritmo-backend.onrender.com"

    fun fechaHoy(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    fun cargarResumenDelDia() {
        val uid = userId ?: return
        scope.launch {
            try {
                val respuesta = withContext(Dispatchers.IO) {
                    val url = URL("$BASE_URL/meals/$uid/summary?date=${fechaHoy()}")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 15000
                    conn.readTimeout = 15000

                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val response = stream.bufferedReader().readText()
                    stream.close()

                    if (code !in 200..299) throw Exception("HTTP $code: $response")
                    response
                }
                val json = JSONObject(respuesta)
                resumenDelDia = ResumenDelDia(
                    totalCalories = json.optInt("totalCalories", 0),
                    totalProtein = json.optInt("totalProtein", 0),
                    totalCarbs = json.optInt("totalCarbs", 0),
                    totalFats = json.optInt("totalFats", 0),
                    cantidadComidas = json.optInt("cantidadComidas", 0)
                )
            } catch (e: Exception) {
                // Si falla el resumen no interrumpimos el resto de la pantalla,
                // simplemente no se muestra el total del día.
            }
        }
    }

    LaunchedEffect(userId) {
        cargarResumenDelDia()
    }

    fun guardarComida() {
        val res = resultado ?: return
        val uid = userId
        if (uid == null) {
            error = "No se pudo guardar: no hay una sesión de usuario activa."
            return
        }

        guardando = true
        guardadoOk = false

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val url = URL("$BASE_URL/meals")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true
                    conn.connectTimeout = 15000
                    conn.readTimeout = 15000

                    val body = JSONObject().apply {
                        put("userId", uid)
                        put("foodName", res.foodName)
                        put("category", res.category)
                        put("portion", res.portion)
                        put("estimatedCalories", res.estimatedCalories)
                        put("calorieRange", res.calorieRange)
                        put("protein", res.protein)
                        put("carbs", res.carbs)
                        put("fats", res.fats)
                        put("confidence", res.confidence)
                        put("observation", res.observation)
                        put("date", fechaHoy())
                    }

                    conn.outputStream.use { output ->
                        output.write(body.toString().toByteArray())
                        output.flush()
                    }

                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val response = stream.bufferedReader().readText()
                    stream.close()

                    if (code !in 200..299) throw Exception("HTTP $code: $response")
                }
                guardadoOk = true
                cargarResumenDelDia()
            } catch (e: Exception) {
                error = "Error al guardar: ${e.message}"
            } finally {
                guardando = false
            }
        }
    }

    val galeriaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
            resultado = null
            error = null
            val stream: InputStream? = context.contentResolver.openInputStream(uri)
            val originalBytes = stream?.readBytes()
            stream?.close()
            if (originalBytes != null) {
                imageBase64 = bitmapAJpegBase64(originalBytes)
                imageMediaType = "image/jpeg"
            }
        }
    }

    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }

    val camaraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { exito: Boolean ->
        val uri = cameraTempUri
        if (exito && uri != null) {
            resultado = null
            error = null
            val stream: InputStream? = context.contentResolver.openInputStream(uri)
            val bytes = stream?.readBytes()
            stream?.close()
            if (bytes != null) {
                imageBase64 = bitmapAJpegBase64(bytes)
                imageMediaType = "image/jpeg"
                imageUri = uri
            }
        }
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

    fun analizarImagen() {
        val base64 = imageBase64 ?: return
        cargando = true
        error = null
        resultado = null
        guardadoOk = false

        scope.launch {
            try {
                val respuesta = withContext(Dispatchers.IO) {
                    val url = URL("$BASE_URL/analyze-food")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true
                    conn.connectTimeout = 20000
                    conn.readTimeout = 30000

                    val body = JSONObject().apply {
                        put("imageBase64", base64)
                        put("mimeType", imageMediaType)
                    }

                    conn.outputStream.use { output ->
                        output.write(body.toString().toByteArray())
                        output.flush()
                    }

                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val response = stream.bufferedReader().readText()
                    stream.close()

                    if (code !in 200..299) {
                        throw Exception("HTTP $code: $response")
                    }

                    response
                }

                // El backend devuelve UN objeto (no un array "detectedFoods"):
                // { isFood, foodName, category, portion, estimatedCalories,
                //   calorieRange, protein, carbs, fats, confidence, observation }
                val json = JSONObject(respuesta)
                val isFood = json.optBoolean("isFood", false)

                if (!isFood) {
                    error = json.optString(
                        "observation",
                        "No se detectó comida en la imagen."
                    )
                } else {
                    resultado = FoodAnalysisResult(
                        foodName = json.optString("foodName", "Alimento no identificado"),
                        category = json.optString("category", ""),
                        portion = json.optString("portion", ""),
                        estimatedCalories = json.optInt("estimatedCalories", 0),
                        calorieRange = json.optString("calorieRange", ""),
                        protein = json.optInt("protein", 0),
                        carbs = json.optInt("carbs", 0),
                        fats = json.optInt("fats", 0),
                        confidence = json.optString("confidence", ""),
                        observation = json.optString("observation", "")
                    )
                }

            } catch (e: Exception) {
                error = if (e is java.net.SocketTimeoutException) {
                    "El servidor tardó demasiado en responder. Intenta de nuevo."
                } else {
                    "Error al analizar: ${e.message}"
                }
            } finally {
                cargando = false
            }
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
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.horizontalPadding)
                .padding(top = 16.dp, bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Analizador de comida",
                fontSize = dimens.titleFontSize,
                fontWeight = FontWeight.Bold,
                color = uiColors.textPrimary,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Toma o sube una foto de tu comida",
                fontSize = dimens.bodyFontSize,
                color = uiColors.textSecondary,
                modifier = Modifier.fillMaxWidth()
            )

            resumenDelDia?.let { resumen ->
                if (resumen.cantidadComidas > 0) {
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
                enabled = imageUri != null && !cargando,
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
                    text = if (cargando) "Analizando..." else "Analizar con IA",
                    fontWeight = FontWeight.Bold,
                    fontSize = dimens.buttonFontSize
                )
            }

            if (cargando) {
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
                        if (res.calorieRange.isNotBlank()) {
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
                        enabled = !guardando,
                        modifier = Modifier.fillMaxWidth().height(dimens.buttonHeight),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TealMedium,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = if (guardando) "Guardando..." else "Guardar en mi historial",
                            fontWeight = FontWeight.Bold,
                            fontSize = dimens.buttonFontSize
                        )
                    }

                    if (guardadoOk) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Comida guardada ✓",
                            fontSize = dimens.labelFontSize,
                            color = TealPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        NavigationBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            containerColor = uiColors.bottomBar,
            contentColor = uiColors.bottomUnselected
        ) {
            NavigationBarItem(selected = false, onClick = { navController.navigate("home") }, icon = { Icon(Icons.Default.Home, contentDescription = null) }, label = { Text("Home") }, colors = bottomItemColors(uiColors))
            NavigationBarItem(selected = false, onClick = { navController.navigate("IA") }, icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) }, label = { Text("IA") }, colors = bottomItemColors(uiColors))
            NavigationBarItem(selected = true, onClick = { }, icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) }, label = { Text("Comida") }, colors = bottomItemColors(uiColors))
            NavigationBarItem(selected = false, onClick = { navController.navigate("agenda") }, icon = { Icon(Icons.Default.DateRange, contentDescription = null) }, label = { Text("Agenda") }, colors = bottomItemColors(uiColors))
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