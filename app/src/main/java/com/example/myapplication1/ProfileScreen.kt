package com.example.myapplication1

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@Composable
fun ProfileScreen(navController: NavController) {
    val context = LocalContext.current
    val session = SessionManager(context)
    val name = session.getName() ?: "Usuario"
    val email = session.getEmail() ?: ""

    val settings by AppSettingsStore.settings.collectAsState()
    val uiColors = appUiColors(settings.temaOscuro)

    Box(modifier = Modifier.fillMaxSize().background(uiColors.background)) {

        Column(
            modifier = Modifier.fillMaxSize().padding(bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF26A69A), Color(0xFF4DB6AC)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(Color.White.copy(alpha = 0.25f), CircleShape)
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    if (email.isNotBlank()) {
                        Text(text = email, fontSize = 13.sp, color = Color(0xFFB2DFDB))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileOption(text = "Editar perfil", icon = "", uiColors = uiColors, onClick = {})
                ProfileOption(text = "Configuración", icon = "", uiColors = uiColors, onClick = { navController.navigate("settings") })
                ProfileOption(
                    text = "Cerrar sesión",
                    icon = "",
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
            NavigationBarItem(selected = false, onClick = { navController.navigate("IA") }, icon = { Icon(Icons.Default.SmartToy, contentDescription = null) }, label = { Text("IA") }, colors = bottomItemColors(uiColors))
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