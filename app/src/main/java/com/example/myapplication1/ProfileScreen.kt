package com.example.myapplication1

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter

@Composable
fun ProfileScreen(navController: NavController) {
    val context = LocalContext.current
    val session = SessionManager(context)

    val settings by AppSettingsStore.settings.collectAsState()
    val uiColors = appUiColors(settings.temaOscuro)

    var displayName by remember { mutableStateOf(session.getDisplayName() ?: session.getName() ?: "Usuario") }
    val email = session.getEmail() ?: ""
    var photoUri by remember { mutableStateOf(session.getPhotoUri()?.let { Uri.parse(it) }) }

    var mostrarDialogNombre by remember { mutableStateOf(false) }
    var nombreTemporal by remember { mutableStateOf(displayName) }

    val galeria = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            photoUri = uri
            session.savePhotoUri(uri.toString())
        }
    }

    if (mostrarDialogNombre) {
        AlertDialog(
            onDismissRequest = { mostrarDialogNombre = false },
            containerColor = uiColors.card,
            title = {
                Text("Editar nombre", fontWeight = FontWeight.Bold, color = uiColors.textPrimary)
            },
            text = {
                OutlinedTextField(
                    value = nombreTemporal,
                    onValueChange = { nombreTemporal = it },
                    placeholder = { Text("Tu nombre") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF26A69A),
                        unfocusedBorderColor = uiColors.border,
                        focusedTextColor = uiColors.textPrimary,
                        unfocusedTextColor = uiColors.textPrimary,
                        focusedContainerColor = uiColors.inputBackground,
                        unfocusedContainerColor = uiColors.inputBackground
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nombreTemporal.isNotBlank()) {
                            displayName = nombreTemporal.trim()
                            session.saveDisplayName(displayName)
                        }
                        mostrarDialogNombre = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26A69A))
                ) { Text("Guardar", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogNombre = false }) {
                    Text("Cancelar", color = uiColors.textMuted)
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(uiColors.background)) {

        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF26A69A), Color(0xFF4DB6AC)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        if (photoUri != null) {
                            Image(
                                painter = rememberAsyncImagePainter(photoUri),
                                contentDescription = "Foto de perfil",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, Color.White, CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .background(Color.White.copy(alpha = 0.25f), CircleShape)
                                    .border(2.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(58.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(0xFF26A69A), CircleShape)
                                .border(1.5.dp, Color.White, CircleShape)
                                .clickable { galeria.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Cambiar foto",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "✏️",
                            fontSize = 14.sp,
                            modifier = Modifier.clickable { nombreTemporal = displayName; mostrarDialogNombre = true }
                        )
                    }

                    if (email.isNotBlank()) {
                        Text(text = email, fontSize = 13.sp, color = Color(0xFFB2DFDB))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProfileOption(text = "Editar nombre", icon = "✏️", uiColors = uiColors, onClick = {
                    nombreTemporal = displayName
                    mostrarDialogNombre = true
                })
                ProfileOption(text = "Cambiar foto", icon = "📷", uiColors = uiColors, onClick = {
                    galeria.launch("image/*")
                })
                ProfileOption(text = "Configuración", icon = "⚙️", uiColors = uiColors, onClick = {
                    navController.navigate("settings")
                })
                ProfileOption(
                    text = "Cerrar sesión",
                    icon = "🚪",
                    uiColors = uiColors,
                    textColor = uiColors.dangerButton,
                    onClick = {
                        session.logout()
                        navController.navigate("login") { popUpTo("home") { inclusive = true } }
                    }
                )
            }
        }

        NavigationBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            containerColor = uiColors.bottomBar,
            contentColor = uiColors.bottomUnselected
        ) {
            NavigationBarItem(selected = false, onClick = { navController.navigate("home") }, icon = { Icon(Icons.Default.Home, contentDescription = null) }, label = { Text("Home") }, colors = bottomItemColors(uiColors))
            NavigationBarItem(selected = false, onClick = { navController.navigate("IA") }, icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) }, label = { Text("IA") }, colors = bottomItemColors(uiColors))
            NavigationBarItem(selected = false, onClick = { navController.navigate("camera") }, icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) }, label = { Text("Comida") }, colors = bottomItemColors(uiColors))
            NavigationBarItem(selected = false, onClick = { navController.navigate("agenda") }, icon = { Icon(Icons.Default.DateRange, contentDescription = null) }, label = { Text("Agenda") }, colors = bottomItemColors(uiColors))
        }
    }
}

@Composable
fun ProfileOption(
    text: String,
    icon: String,
    uiColors: AppUiColors,
    textColor: Color = uiColors.textPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(uiColors.card, RoundedCornerShape(16.dp))
            .border(1.dp, uiColors.border, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor, modifier = Modifier.weight(1f))
        Text(text = "›", fontSize = 20.sp, color = uiColors.textMuted)
    }
}