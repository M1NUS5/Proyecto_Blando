package com.example.myapplication1

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

/**
 * Aviso que aparece en el resto de las secciones cuando hay un entrenamiento en
 * curso.
 *
 * El estado del entrenamiento vive en [RunDataStore], que es global, por lo que
 * la sesion continua aunque el usuario cambie de pestana. Sin este aviso podria
 * olvidar que la dejo activa y creer que la aplicacion no responde o que sus
 * metricas son incorrectas. Al tocarlo regresa a la pantalla de entrenamiento.
 */
@Composable
fun EntrenamientoActivoBanner(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    if (RunDataStore.estadoEntrenamiento != "CORRIENDO" &&
        RunDataStore.estadoEntrenamiento != "PAUSADO"
    ) {
        return
    }

    val pausado = RunDataStore.estadoEntrenamiento == "PAUSADO"
    val fondo = if (pausado) Color(0xFF8D6E63) else Color(0xFF26A69A)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(fondo, RoundedCornerShape(14.dp))
            .clickable { navController.irASeccion(RUTA_INICIO) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = Icons.Default.DirectionsRun,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = if (pausado) "Entrenamiento en pausa" else "Entrenamiento en curso",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = "Volver",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
