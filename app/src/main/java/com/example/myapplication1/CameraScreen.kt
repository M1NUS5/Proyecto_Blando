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

@Composable
fun CameraScreen(navController: NavController) {
    val context = LocalContext.current
    val settings by AppSettingsStore.settings.collectAsState()
    val uiColors = appUiColors(settings.temaOscuro)
    val dimens = rememberResponsiveDimens()
    val scope = rememberCoroutineScope()

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageBase64 by remember { mutableStateOf<String?>(null) }
    var imageMediaType by remember { mutableStateOf("image/jpeg") }
    var resultado by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

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
                val bitmap = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size)
                val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, 800, 800, true)
                val compressed = ByteArrayOutputStream()
                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, compressed)
                imageBase64 = Base64.encodeToString(compressed.toByteArray(), Base64.NO_WRAP)
            }
        }
    }

    val camaraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            resultado = null
            error = null
            val stream = ByteArrayOutputStream()
            val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, 800, 800, true)
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, stream)
            val bytes = stream.toByteArray()
            imageBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val tmpFile = java.io.File(context.cacheDir, "foto_comida.jpg")
            tmpFile.writeBytes(bytes)
            imageUri = Uri.fromFile(tmpFile)
        }
    }

    fun analizarImagen() {
        val base64 = imageBase64 ?: return
        cargando = true
        error = null
        resultado = null

        scope.launch {
            try {
                val respuesta = withContext(Dispatchers.IO) {
                    val url = URL("https://api.anthropic.com/v1/messages")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("x-api-key", BuildConfig.ANTHROPIC_API_KEY)
                    conn.setRequestProperty("anthropic-version", "2023-06-01")
                    conn.doOutput = true

                    val body = JSONObject().apply {
                        put("model", "claude-haiku-4-5-20251001")
                        put("max_tokens", 1024)
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("type", "image")
                                        put("source", JSONObject().apply {
                                            put("type", "base64")
                                            put("media_type", "image/jpeg")
                                            put("data", base64)
                                        })
                                    })
                                    put(JSONObject().apply {
                                        put("type", "text")
                                        put("text", "Analiza esta imagen de comida y proporciona una estimación nutricional. Responde SOLO en este formato exacto:\n\nALIMENTO: [nombre del alimento o platillo]\nCALORÍAS: [número] kcal\nPROTEÍNAS: [número] g\nCARBOHIDRATOS: [número] g\nGRASAS: [número] g\nNOTA: [observación breve en una línea]\n\nSi no puedes identificar comida en la imagen, responde solo: NO_COMIDA")
                                    })
                                })
                            })
                        })
                    }

                    conn.outputStream.write(body.toString().toByteArray())
                    conn.outputStream.flush()

                    val code = conn.responseCode
                    val stream = if (code == 200) conn.inputStream else conn.errorStream
                    val response = stream.bufferedReader().readText()
                    stream.close()
                    response
                }

                val json = JSONObject(respuesta)

                if (json.has("error")) {
                    val errorMsg = json.getJSONObject("error").optString("message", "Error desconocido")
                    throw Exception("API error: $errorMsg")
                }

                if (!json.has("content")) {
                    throw Exception("Respuesta inesperada: $respuesta")
                }

                val texto = json.getJSONArray("content")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()

                if (texto == "NO_COMIDA") {
                    error = "No se detectó comida en la imagen. Intenta con otra foto."
                } else {
                    resultado = texto
                }
            } catch (e: Exception) {
                error = "Error al analizar: ${e.message}"
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

            Spacer(modifier = Modifier.height(20.dp))

            if (imageUri != null) {
                androidx.compose.foundation.Image(
                    painter = rememberAsyncImagePainter(imageUri),
                    contentDescription = "Foto de comida",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(2.dp, TealPrimary, RoundedCornerShape(20.dp))
                )
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
                    onClick = { camaraLauncher.launch(null) },
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Resultado nutricional",
                        fontSize = dimens.titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = TealPrimary
                    )

                    res.lines().forEach { linea ->
                        if (linea.isNotBlank()) {
                            val partes = linea.split(":", limit = 2)
                            if (partes.size == 2) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = partes[0].trim(),
                                        fontSize = dimens.bodyFontSize,
                                        fontWeight = FontWeight.Bold,
                                        color = uiColors.textPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = partes[1].trim(),
                                        fontSize = dimens.bodyFontSize,
                                        color = TealMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                HorizontalDivider(color = uiColors.border, thickness = 0.5.dp)
                            }
                        }
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