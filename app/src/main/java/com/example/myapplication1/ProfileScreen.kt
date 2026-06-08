package com.example.myapplication1

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun ProfileScreen(navController: NavController) {

    val context = LocalContext.current
    val session = SessionManager(context)
    val name = session.getName() ?: "Usuario"

    Box(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF2F2F2))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Box(
                modifier = Modifier
                    .padding(top = 40.dp)
                    .background(Color.Gray, shape = CircleShape)
                    .padding(40.dp)
            ) {
                Text("👤")
            }

            Text(
                text = name,
                modifier = Modifier.padding(top = 16.dp),
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(20.dp))

            ProfileOption(
                text = "Editar perfil",
                onClick = {
                    // Por ahora lo dejamos visual
                }
            )

            ProfileOption(
                text = "Configuración",
                onClick = {
                    navController.navigate("settings")
                }
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

        NavigationBar(
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            NavigationBarItem(
                selected = false,
                onClick = { navController.navigate("home") },
                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                label = { Text("Home") }
            )
            NavigationBarItem(
                selected = false,
                onClick = { navController.navigate("IA") },
                icon = { Icon(Icons.Default.SmartToy, contentDescription = null) },
                label = { Text("IA") }
            )
            NavigationBarItem(
                selected = false,
                onClick = { navController.navigate("agenda") },
                icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                label = { Text("Agenda") }
            )
        }
    }
}

@Composable
fun ProfileOption(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(Color.White, shape = RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Text(text = text)
    }
}