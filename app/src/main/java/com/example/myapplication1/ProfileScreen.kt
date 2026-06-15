package com.example.myapplication1

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage

@Composable
fun ProfileScreen(navController: NavController) {

    val context = LocalContext.current
    val session = remember { SessionManager(context) }

    val settings by AppSettingsStore.settings.collectAsState()
    val uiColors = appUiColors(settings.temaOscuro)

    var name        by remember { mutableStateOf(session.getName() ?: "Usuario") }
    var email       by remember { mutableStateOf(session.getEmail() ?: "") }
    var photoUri    by remember { mutableStateOf<Uri?>(
        session.getProfilePhotoUri()?.let { Uri.parse(it) }
    ) }

    var editando    by remember { mutableStateOf(false) }
    var nombreEdit  by remember { mutableStateOf(name) }
    var showSnackbar by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            photoUri = uri
            session.saveProfilePhotoUri(uri.toString())
        }
    }

    LaunchedEffect(showSnackbar) {
        if (showSnackbar) {
            kotlinx.coroutines.delay(2000)
            showSnackbar = false
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
                .verticalScroll(rememberScrollState())
                .padding(bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProfileTopBar(temaOscuro = settings.temaOscuro)

            Spacer(modifier = Modifier.height(28.dp))

            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(uiColors.topBarStart, uiColors.topBarEnd)
                            )
                        )
                        .clickable { imagePicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUri != null) {
                        AsyncImage(
                            model = photoUri,
                            contentDescription = "Foto de perfil",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(60.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(uiColors.primaryButton, CircleShape)
                        .border(2.dp, uiColors.background, CircleShape)
                        .clickable { imagePicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Cambiar foto",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Toca la foto para cambiarla",
                fontSize = 11.sp,
                color = uiColors.textMuted
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (editando) {
                OutlinedTextField(
                    value = nombreEdit,
                    onValueChange = { nombreEdit = it },
                    label = { Text("Nombre") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = uiColors.primaryButton
                        )
                    },
                    trailingIcon = {
                        Row {
                            IconButton(onClick = {
                                if (nombreEdit.isNotBlank()) {
                                    name = nombreEdit
                                    session.updateName(nombreEdit)
                                    editando = false
                                    showSnackbar = true
                                }
                            }) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Guardar",
                                    tint = uiColors.success
                                )
                            }
                            IconButton(onClick = {
                                nombreEdit = name
                                editando = false
                            }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Cancelar",
                                    tint = uiColors.dangerButton
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = alyraTextFieldColors(uiColors),
                    singleLine = true
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = name,
                        fontSize = 22.sp,
                        color = uiColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar nombre",
                        tint = uiColors.primaryButton,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable {
                                nombreEdit = name
                                editando = true
                            }
                    )
                }
            }

                        Text(
                text = email,
                fontSize = 13.sp,
                color = uiColors.textMuted,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (showSnackbar) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .background(
                            color = uiColors.success.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(1.dp, uiColors.success.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "✓ Nombre actualizado correctamente",
                        color = uiColors.success,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfileOption(
                    text = "Editar nombre",
                    onClick = {
                        nombreEdit = name
                        editando = true
                    }
                )

                ProfileOption(
                    text = "Cambiar foto de perfil",
                    onClick = { imagePicker.launch("image/*") }
                )

                ProfileOption(
                    text = "Configuración",
                    onClick = { navController.navigate("settings") }
                )

                ProfileOption(
                    text = "Cerrar sesión",
                    onClick = {
                        session.logout()
                        navController.navigate("login") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        NavigationBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            containerColor = uiColors.bottomBar,
            contentColor = uiColors.bottomUnselected
        ) {
            NavigationBarItem(
                selected = false,
                onClick = { navController.navigate("home") },
                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                label = { Text("Home") },
                colors = bottomItemColors(uiColors)
            )
            NavigationBarItem(
                selected = false,
                onClick = { navController.navigate("IA") },
                icon = { Icon(Icons.Default.SmartToy, contentDescription = null) },
                label = { Text("IA") },
                colors = bottomItemColors(uiColors)
            )
            NavigationBarItem(
                selected = false,
                onClick = { navController.navigate("agenda") },
                icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                label = { Text("Agenda") },
                colors = bottomItemColors(uiColors)
            )
        }
    }
}


@Composable
fun ProfileOption(
    text: String,
    onClick: () -> Unit
) {
    val settings by AppSettingsStore.settings.collectAsState()
    val uiColors = appUiColors(settings.temaOscuro)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(uiColors.card, shape = RoundedCornerShape(16.dp))
            .border(1.dp, uiColors.border, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(18.dp)
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            color = if (text == "Cerrar sesión") uiColors.dangerButton else uiColors.textPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ProfileTopBar(temaOscuro: Boolean) {
    val uiColors = appUiColors(temaOscuro)
    val gradient = Brush.horizontalGradient(
        listOf(uiColors.topBarStart, uiColors.topBarEnd)
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(gradient)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Perfil",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
        )
    }
}