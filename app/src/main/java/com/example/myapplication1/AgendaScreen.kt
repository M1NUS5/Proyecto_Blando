package com.example.myapplication1

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun AgendaScreen(navController: NavController) {

    val context = LocalContext.current
    val dimens = rememberResponsiveDimens()

    val settings by AppSettingsStore.settings.collectAsState()
    val uiColors = appUiColors(settings.temaOscuro)

    val sessionManager = remember { SessionManager(context) }
    val userId = sessionManager.getUserId() ?: ""

    var actividades by remember { mutableStateOf<List<ActivityItem>>(emptyList()) }
    var mensaje by remember { mutableStateOf("Cargando actividades...") }
    var actividadParaEliminar by remember { mutableStateOf<ActivityItem?>(null) }
    var cargando by remember { mutableStateOf(true) }

    val fechaHoy = remember { obtenerFechaActualString() }
    val calendarioHoy = remember { convertirFechaCalendar(fechaHoy) ?: Calendar.getInstance() }

    var mesActual by remember { mutableIntStateOf(calendarioHoy.get(Calendar.MONTH) + 1) }
    var anioActual by remember { mutableIntStateOf(calendarioHoy.get(Calendar.YEAR)) }
    var fechaSeleccionada by remember { mutableStateOf(fechaHoy) }

    fun cargarActividades() {
        if (userId.isBlank()) {
            mensaje = "No hay sesión iniciada"
            actividades = emptyList()
            cargando = false
            return
        }

        cargando = true
        mensaje = "Cargando actividades..."

        RetrofitClient.instance.getActivities(userId)
            .enqueue(object : Callback<List<ActivityItem>> {
                override fun onResponse(
                    call: Call<List<ActivityItem>>,
                    response: Response<List<ActivityItem>>
                ) {
                    cargando = false

                    if (response.isSuccessful) {
                        actividades = response.body().orEmpty()

                        mensaje = if (actividades.isEmpty()) {
                            "No hay entrenamientos guardados todavía"
                        } else {
                            "Entrenamientos cargados: ${actividades.size}"
                        }
                    } else {
                        mensaje = "Error al obtener actividades. Código: ${response.code()}"
                    }
                }

                override fun onFailure(call: Call<List<ActivityItem>>, t: Throwable) {
                    cargando = false
                    mensaje = "Fallo de conexión: ${t.message}"
                }
            })
    }

    fun eliminarActividad(actividad: ActivityItem) {
        val idActividad = actividad._id

        if (idActividad.isNullOrBlank()) {
            actividadParaEliminar = null
            mensaje = "No se puede eliminar: la actividad no tiene ID"

            Toast.makeText(
                context,
                "No se puede eliminar: la actividad no tiene ID",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        mensaje = "Eliminando entrenamiento..."

        RetrofitClient.instance.deleteActivity(idActividad)
            .enqueue(object : Callback<DeleteResponse> {
                override fun onResponse(
                    call: Call<DeleteResponse>,
                    response: Response<DeleteResponse>
                ) {
                    actividadParaEliminar = null

                    if (response.isSuccessful) {
                        actividades = actividades.filter { it._id != idActividad }

                        mensaje = response.body()?.message ?: "Entrenamiento eliminado correctamente"

                        Toast.makeText(
                            context,
                            "Entrenamiento eliminado",
                            Toast.LENGTH_SHORT
                        ).show()

                        cargarActividades()
                    } else {
                        mensaje = "No se pudo eliminar. Código: ${response.code()}"

                        Toast.makeText(
                            context,
                            "Error al eliminar. Código: ${response.code()}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onFailure(call: Call<DeleteResponse>, t: Throwable) {
                    actividadParaEliminar = null
                    mensaje = "Fallo de conexión al eliminar: ${t.message}"

                    Toast.makeText(
                        context,
                        "Fallo de conexión: ${t.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
    }

    LaunchedEffect(userId) {
        cargarActividades()
    }

    val actividadesPorFecha = remember(actividades) {
        actividades.groupBy { it.date }
    }

    val actividadesDelMes = remember(actividades, mesActual, anioActual) {
        actividades.filter { actividad ->
            val calendario = convertirFechaCalendar(actividad.date)

            calendario != null &&
                    calendario.get(Calendar.MONTH) + 1 == mesActual &&
                    calendario.get(Calendar.YEAR) == anioActual
        }
    }

    val actividadesDelDia = actividadesPorFecha[fechaSeleccionada].orEmpty()

    val totalEntrenamientosMes = actividadesDelMes.size
    val totalKmMes = actividadesDelMes.sumOf { it.distance }
    val totalMinutosMes = actividadesDelMes.sumOf { it.duration }

    val pacePromedioMes = if (actividadesDelMes.isNotEmpty()) {
        actividadesDelMes
            .map { it.pace }
            .filter { it > 0.0 }
            .average()
            .takeIf { !it.isNaN() } ?: 0.0
    } else {
        0.0
    }

    val distanciaMesMostrada = convertirDistancia(totalKmMes, settings.unidadPrincipal)
    val unidadDistancia = if (settings.unidadPrincipal == "millas") "Mi" else "Km"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(uiColors.background)
    ) {

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 14.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            item {
                EntrenamientoActivoBanner(
                    navController = navController,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = "Agenda / Historial",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = uiColors.textPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (userId.isNotEmpty()) {
                        "Entrenamientos guardados automáticamente"
                    } else {
                        "No hay sesión iniciada"
                    },
                    fontSize = 14.sp,
                    color = uiColors.textMuted
                )
            }

            item {
                ModernAgendaCard(
                    title = "Resumen mensual",
                    subtitle = "${nombreMes(mesActual)} $anioActual",
                    uiColors = uiColors
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AgendaStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Sesiones",
                            value = totalEntrenamientosMes.toString(),
                            uiColors = uiColors
                        )

                        AgendaStatCard(
                            modifier = Modifier.weight(1f),
                            label = unidadDistancia,
                            value = String.format(Locale.US, "%.2f", distanciaMesMostrada),
                            uiColors = uiColors
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AgendaStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Minutos",
                            value = String.format(Locale.US, "%.1f", totalMinutosMes),
                            uiColors = uiColors
                        )

                        AgendaStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Pace prom.",
                            value = if (pacePromedioMes > 0) {
                                String.format(Locale.US, "%.2f", pacePromedioMes)
                            } else {
                                "--"
                            },
                            uiColors = uiColors
                        )
                    }
                }
            }

            item {
                ModernAgendaCard(
                    title = "Calendario de entrenamientos",
                    subtitle = "Selecciona un día para ver su resumen",
                    uiColors = uiColors
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (mesActual == 1) {
                                    mesActual = 12
                                    anioActual -= 1
                                } else {
                                    mesActual -= 1
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = uiColors.primaryButton,
                                contentColor = uiColors.primaryButtonText
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Anterior")
                        }

                        Text(
                            text = "${nombreMes(mesActual)} $anioActual",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = uiColors.textPrimary
                        )

                        Button(
                            onClick = {
                                if (mesActual == 12) {
                                    mesActual = 1
                                    anioActual += 1
                                } else {
                                    mesActual += 1
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = uiColors.primaryButton,
                                contentColor = uiColors.primaryButtonText
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Siguiente")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    CalendarMonthView(
                        mes = mesActual,
                        anio = anioActual,
                        selectedDate = fechaSeleccionada,
                        fechasConActividad = actividadesPorFecha.keys,
                        uiColors = uiColors,
                        onDateSelected = { fecha ->
                            fechaSeleccionada = fecha
                        }
                    )
                }
            }

            item {
                ModernAgendaCard(
                    title = "Resumen del día",
                    subtitle = formatearFechaLarga(fechaSeleccionada),
                    uiColors = uiColors
                ) {
                    if (actividadesDelDia.isEmpty()) {
                        Text(
                            text = "No hay entrenamientos registrados en este día.",
                            fontSize = 15.sp,
                            color = uiColors.textMuted
                        )
                    } else {
                        val kmDia = actividadesDelDia.sumOf { it.distance }
                        val minutosDia = actividadesDelDia.sumOf { it.duration }

                        val paceDia = actividadesDelDia
                            .map { it.pace }
                            .filter { it > 0.0 }
                            .average()
                            .takeIf { !it.isNaN() } ?: 0.0

                        val distanciaDiaMostrada =
                            convertirDistancia(kmDia, settings.unidadPrincipal)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AgendaStatCard(
                                modifier = Modifier.weight(1f),
                                label = "Sesiones",
                                value = actividadesDelDia.size.toString(),
                                uiColors = uiColors
                            )

                            AgendaStatCard(
                                modifier = Modifier.weight(1f),
                                label = unidadDistancia,
                                value = String.format(Locale.US, "%.2f", distanciaDiaMostrada),
                                uiColors = uiColors
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AgendaStatCard(
                                modifier = Modifier.weight(1f),
                                label = "Duración",
                                value = "${String.format(Locale.US, "%.1f", minutosDia)} min",
                                uiColors = uiColors
                            )

                            AgendaStatCard(
                                modifier = Modifier.weight(1f),
                                label = "Pace",
                                value = if (paceDia > 0) {
                                    String.format(Locale.US, "%.2f", paceDia)
                                } else {
                                    "--"
                                },
                                uiColors = uiColors
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Entrenamientos del día",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = uiColors.textPrimary
                )
            }

            if (cargando) {
                item {
                    CargandoAgendaCard(
                        mensaje = "Cargando tus entrenamientos...",
                        uiColors = uiColors
                    )
                }
            } else if (actividadesDelDia.isEmpty()) {
                item {
                    EmptyAgendaCard(
                        mensaje = mensaje,
                        uiColors = uiColors
                    )
                }
            } else {
                items(actividadesDelDia) { actividad ->
                    ActivityHistoryCard(
                        actividad = actividad,
                        unidadPrincipal = settings.unidadPrincipal,
                        uiColors = uiColors,
                        onDeleteClick = {
                            actividadParaEliminar = actividad
                        }
                    )
                }
            }

            item {
                Button(
                    onClick = { cargarActividades() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dimens.buttonHeight),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = uiColors.primaryButton,
                        contentColor = uiColors.primaryButtonText
                    )
                ) {
                    Text(
                        text = "Actualizar historial",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        actividadParaEliminar?.let { actividad ->
            AlertDialog(
                onDismissRequest = {
                    actividadParaEliminar = null
                },
                containerColor = uiColors.card,
                title = {
                    Text(
                        text = "Eliminar entrenamiento",
                        color = uiColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "¿Seguro que quieres eliminar este entrenamiento?\n\n" +
                                "${actividad.title.ifBlank { "Entrenamiento finalizado" }}\n" +
                                "${actividad.date}\n" +
                                "${String.format(Locale.US, "%.2f", actividad.distance)} km",
                        color = uiColors.textSecondary
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            eliminarActividad(actividad)
                        }
                    ) {
                        Text(
                            text = "Eliminar",
                            color = uiColors.dangerButton,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            actividadParaEliminar = null
                        }
                    ) {
                        Text(
                            text = "Cancelar",
                            color = uiColors.primaryButton
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
            NavigationBarItem(
                selected = false,
                onClick = { navController.irASeccion("home") },
                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                label = { Text("Home") },
                colors = agendaBottomItemColors(uiColors)
            )

            NavigationBarItem(
                selected = false,
                onClick = { navController.irASeccion("IA") },
                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                label = { Text("IA") },
                colors = agendaBottomItemColors(uiColors)
            )

            NavigationBarItem(
                selected = false,
                onClick = { navController.irASeccion("camera") },
                icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                label = { Text("Comida") },
                colors = agendaBottomItemColors(uiColors)
            )

            NavigationBarItem(
                selected = true,
                onClick = { },
                icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                label = { Text("Agenda") },
                colors = agendaBottomItemColors(uiColors)
            )
        }
    }
}

@Composable
fun CalendarMonthView(
    mes: Int,
    anio: Int,
    selectedDate: String,
    fechasConActividad: Set<String>,
    uiColors: AppUiColors,
    onDateSelected: (String) -> Unit
) {
    val dimens = rememberResponsiveDimens()
    val diasSemana = listOf("L", "M", "M", "J", "V", "S", "D")
    val diasDelMes = obtenerDiasDelMes(mes, anio)
    val espaciosIniciales = obtenerEspaciosInicialesCalendario(mes, anio)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            diasSemana.forEach { dia ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dia,
                        fontSize = dimens.calendarDayFont,
                        fontWeight = FontWeight.Bold,
                        color = uiColors.textMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        val totalCeldas = espaciosIniciales + diasDelMes
        val filas = (totalCeldas + 6) / 7

        repeat(filas) { fila ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(7) { columna ->
                    val index = fila * 7 + columna
                    val numeroDia = index - espaciosIniciales + 1

                    if (numeroDia in 1..diasDelMes) {
                        val fecha = crearFechaString(anio, mes, numeroDia)
                        val tieneActividad = fechasConActividad.contains(fecha)
                        val seleccionado = fecha == selectedDate

                        DayCell(
                            day = numeroDia,
                            selected = seleccionado,
                            hasActivity = tieneActividad,
                            uiColors = uiColors,
                            onClick = { onDateSelected(fecha) },
                            modifier = Modifier.weight(1f).aspectRatio(1f)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
fun DayCell(
    day: Int,
    selected: Boolean,
    hasActivity: Boolean,
    uiColors: AppUiColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()
    val background = when {
        selected -> uiColors.primaryButton
        hasActivity -> uiColors.primaryButton.copy(alpha = 0.18f)
        else -> uiColors.cardSecondary
    }

    val textColor = when {
        selected -> uiColors.primaryButtonText
        hasActivity -> uiColors.primaryButton
        else -> uiColors.textPrimary
    }

    Box(
        modifier = modifier
            .padding(2.dp)
            .background(
                color = background,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = if (hasActivity || selected) {
                    uiColors.primaryButton
                } else {
                    uiColors.border
                },
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.toString(),
                fontSize = dimens.calendarDayFont,
                fontWeight = FontWeight.Bold,
                color = textColor
            )

            if (hasActivity) {
                Spacer(modifier = Modifier.height(2.dp))

                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(
                            color = if (selected) uiColors.primaryButtonText else uiColors.primaryButton,
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

@Composable
fun ActivityHistoryCard(
    actividad: ActivityItem,
    unidadPrincipal: String,
    uiColors: AppUiColors,
    onDeleteClick: () -> Unit
) {
    val distanciaMostrada = convertirDistancia(actividad.distance, unidadPrincipal)
    val unidadDistancia = if (unidadPrincipal == "millas") "mi" else "km"

    ModernAgendaCard(
        title = actividad.title.ifBlank { "Entrenamiento finalizado" },
        subtitle = actividad.date,
        uiColors = uiColors
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AgendaStatCard(
                modifier = Modifier.weight(1f),
                label = "Distancia",
                value = "${String.format(Locale.US, "%.2f", distanciaMostrada)} $unidadDistancia",
                uiColors = uiColors
            )

            AgendaStatCard(
                modifier = Modifier.weight(1f),
                label = "Duración",
                value = "${String.format(Locale.US, "%.1f", actividad.duration)} min",
                uiColors = uiColors
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AgendaStatCard(
                modifier = Modifier.weight(1f),
                label = "Pace",
                value = if (actividad.pace > 0.0) {
                    String.format(Locale.US, "%.2f", actividad.pace)
                } else {
                    "--"
                },
                uiColors = uiColors
            )

            AgendaStatCard(
                modifier = Modifier.weight(1f),
                label = "Pasos",
                value = if (actividad.steps > 0) actividad.steps.toString() else "--",
                uiColors = uiColors
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AgendaStatCard(
                modifier = Modifier.weight(1f),
                label = "BPM",
                value = if (actividad.bpm > 0) actividad.bpm.toString() else "--",
                uiColors = uiColors
            )

            AgendaStatCard(
                modifier = Modifier.weight(1f),
                label = "Acel.",
                value = if (actividad.acceleration > 0.0) {
                    String.format(Locale.US, "%.2f", actividad.acceleration)
                } else {
                    "--"
                },
                uiColors = uiColors
            )
        }

        if (actividad.iaLabel.isNotBlank() || actividad.iaRecommendation.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))

            AgendaInfoBlock(
                title = "Análisis IA",
                text = buildString {
                    if (actividad.iaLabel.isNotBlank()) {
                        append("Estado: ${actividad.iaLabel}")
                    }

                    if (actividad.iaRecommendation.isNotBlank()) {
                        if (isNotBlank()) append("\n")
                        append("Recomendación: ${actividad.iaRecommendation}")
                    }
                },
                uiColors = uiColors
            )
        }

        if (actividad.notes.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))

            AgendaInfoBlock(
                title = "Notas",
                text = actividad.notes,
                uiColors = uiColors
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onDeleteClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = uiColors.dangerButton,
                contentColor = Color.White
            )
        ) {
            Text(
                text = "Eliminar entrenamiento",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AgendaInfoBlock(
    title: String,
    text: String,
    uiColors: AppUiColors
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = uiColors.cardSecondary,
                shape = RoundedCornerShape(14.dp)
            )
            .border(
                width = 1.dp,
                color = uiColors.border,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = uiColors.textPrimary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = text,
            fontSize = 14.sp,
            color = uiColors.textSecondary
        )
    }
}

/**
 * Indicador de carga con la misma forma que las tarjetas del historial, para que
 * el usuario perciba que la informacion esta en camino en lugar de una pantalla
 * vacia. Es especialmente util cuando el servidor tarda en responder.
 */
@Composable
fun CargandoAgendaCard(
    mensaje: String,
    uiColors: AppUiColors
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = uiColors.card),
        elevation = CardDefaults.cardElevation(5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(uiColors.card)
                .padding(24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = uiColors.primaryButton
            )

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = mensaje,
                fontSize = 15.sp,
                color = uiColors.textSecondary
            )
        }
    }
}

@Composable
fun EmptyAgendaCard(
    mensaje: String,
    uiColors: AppUiColors
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = uiColors.card),
        elevation = CardDefaults.cardElevation(5.dp)
    ) {
        Column(
            modifier = Modifier
                .background(uiColors.card)
                .padding(18.dp)
        ) {
            Text(
                text = "Sin entrenamientos en este día",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = uiColors.textPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = mensaje,
                fontSize = 14.sp,
                color = uiColors.textMuted
            )
        }
    }
}

@Composable
fun ModernAgendaCard(
    title: String,
    subtitle: String,
    uiColors: AppUiColors,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = uiColors.card),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Column(
            modifier = Modifier
                .background(uiColors.card)
                .padding(18.dp)
        ) {
            Text(
                text = title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = uiColors.textPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = uiColors.textMuted
            )

            Spacer(modifier = Modifier.height(16.dp))

            content()
        }
    }
}

@Composable
fun AgendaStatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    uiColors: AppUiColors
) {
    Box(
        modifier = modifier
            .background(
                color = uiColors.cardSecondary,
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 1.dp,
                color = uiColors.border,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(vertical = 14.dp, horizontal = 10.dp)
    ) {
        Column {
            Text(
                text = label,
                fontSize = 12.sp,
                color = uiColors.textMuted
            )

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = uiColors.textPrimary
            )
        }
    }
}

@Composable
fun agendaBottomItemColors(
    uiColors: AppUiColors
) = NavigationBarItemDefaults.colors(
    selectedIconColor = uiColors.textPrimary,
    selectedTextColor = uiColors.textPrimary,
    indicatorColor = uiColors.bottomSelected,
    unselectedIconColor = uiColors.bottomUnselected,
    unselectedTextColor = uiColors.bottomUnselected
)

fun obtenerFechaActualString(): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}

fun convertirFechaCalendar(fecha: String): Calendar? {
    return try {
        val formato = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        formato.isLenient = false

        val date = formato.parse(fecha) ?: return null

        Calendar.getInstance().apply {
            time = date
        }
    } catch (e: Exception) {
        null
    }
}

fun crearFechaString(
    anio: Int,
    mes: Int,
    dia: Int
): String {
    return String.format(Locale.US, "%04d-%02d-%02d", anio, mes, dia)
}

fun obtenerDiasDelMes(
    mes: Int,
    anio: Int
): Int {
    val calendario = Calendar.getInstance()

    calendario.set(Calendar.YEAR, anio)
    calendario.set(Calendar.MONTH, mes - 1)
    calendario.set(Calendar.DAY_OF_MONTH, 1)

    return calendario.getActualMaximum(Calendar.DAY_OF_MONTH)
}

fun obtenerEspaciosInicialesCalendario(
    mes: Int,
    anio: Int
): Int {
    val calendario = Calendar.getInstance()

    calendario.set(Calendar.YEAR, anio)
    calendario.set(Calendar.MONTH, mes - 1)
    calendario.set(Calendar.DAY_OF_MONTH, 1)

    val diaSemana = calendario.get(Calendar.DAY_OF_WEEK)

    return when (diaSemana) {
        Calendar.MONDAY -> 0
        Calendar.TUESDAY -> 1
        Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3
        Calendar.FRIDAY -> 4
        Calendar.SATURDAY -> 5
        Calendar.SUNDAY -> 6
        else -> 0
    }
}

fun nombreMes(mes: Int): String {
    val nombre = DateFormatSymbols(Locale("es", "MX")).months[mes - 1]

    return nombre.replaceFirstChar {
        it.uppercase(Locale("es", "MX"))
    }
}

fun formatearFechaLarga(fecha: String): String {
    val calendario = convertirFechaCalendar(fecha) ?: return fecha

    val locale = Locale("es", "MX")
    val diasSemana = DateFormatSymbols(locale).weekdays
    val meses = DateFormatSymbols(locale).months

    val diaSemana = diasSemana[calendario.get(Calendar.DAY_OF_WEEK)]
        .replaceFirstChar { it.uppercase(locale) }

    val dia = calendario.get(Calendar.DAY_OF_MONTH)
    val mes = meses[calendario.get(Calendar.MONTH)]
    val anio = calendario.get(Calendar.YEAR)

    return "$diaSemana $dia de $mes de $anio"
}

fun convertirDistancia(
    km: Double,
    unidadPrincipal: String
): Double {
    return if (unidadPrincipal == "millas") {
        km * 0.621371
    } else {
        km
    }
}