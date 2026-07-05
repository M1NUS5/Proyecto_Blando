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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import java.io.ByteArrayOutputStream
import java.io.InputStream

private val TealPrimary = Color(0xFF26A69A)
private val TealMedium  = Color(0xFF4DB6AC)

@Composable
fun CameraScreen(navController: NavController) {
    val context = LocalContext.current
    val settings by AppSettingsStore.settings.collectAsState()
    val uiColors = appUiColors(settings.temaOscuro)
    val dimens = rememberResponsiveDimens()

    var imageUri by remember { mutableStateOf<Uri?>(null) }

    val galeriaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
        }
    }

    val camaraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val stream = ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, stream)
            val bytes = stream.toByteArray()
            val tmpFile = java.io.File(context.cacheDir, "foto_comida.jpg")
            tmpFile.writeBytes(bytes)
            imageUri = Uri.fromFile(tmpFile)
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

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TealPrimary.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
                    .border(1.5.dp, TealPrimary.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "IA en desarrollo",
                        fontSize = dimens.titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = TealPrimary
                    )
                    Text(
                        text = "El análisis nutricional por IA estará disponible próximamente. Un modelo personalizado calculará calorías, proteínas, carbohidratos y grasas de tus comidas.",
                        fontSize = dimens.bodyFontSize,
                        color = uiColors.textSecondary,
                        textAlign = TextAlign.Center
                    )
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