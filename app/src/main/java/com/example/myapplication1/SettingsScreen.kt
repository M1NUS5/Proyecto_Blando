package com.example.myapplication1

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

private val TealPrimary = Color(0xFF26A69A)

/** Pantalla de configuracion, agrupada por lo que el usuario hace en cada parte. */
@Composable
fun SettingsScreen(
    navController: NavController,
    onThemeChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) { AppSettingsStore.cargar(context) }

    val settings by AppSettingsStore.settings.collectAsState()
    val datosReloj by DatosRelojStore.datos.collectAsState()
    val uiColors = appUiColors(settings.temaOscuro)

    val nombre by PerfilStore.nombre.collectAsState()
    val perfilFisico by PerfilStore.fisico.collectAsState()

    val sessionManager = SessionManager(context)
    val correo = sessionManager.getEmail().orEmpty()

    // Al incrementarse se vuelve a preguntar al sistema por los relojes; sirve
    // para el boton de "Buscar de nuevo" tras conectar el reloj desde fuera.
    var refrescosReloj by remember { mutableIntStateOf(0) }

    fun actualizarConfiguracion(nuevaConfig: AppSettings) {
        AppSettingsStore.actualizar(context, nuevaConfig)
        enviarConfiguracionAlReloj(context, nuevaConfig)
    }

    fun cerrarSesion() {
        limpiarSesionLocal(context)

        // Sin esto, el nombre y la foto del usuario anterior seguirian en
        // memoria y apareceran en el encabezado de quien inicie sesion despues.
        PerfilStore.limpiar()
        AnalisisComidaStore.limpiar()

        navController.navigate("login") { popUpTo(navController.graph.startDestinationId) { inclusive = true } }
    }

    Box(modifier = Modifier.fillMaxSize().background(uiColors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 24.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = uiColors.textPrimary)
                }
                Text("Configuración", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = uiColors.textPrimary)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Cuenta -----------------------------------------------------
            // Encabezado, no tarjeta: identifica de quien es la sesion sin
            // competir visualmente con los ajustes, que es lo que se viene a
            // cambiar aqui.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(uiColors.cardSecondary, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = nombre,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = uiColors.textPrimary
                )
                if (correo.isNotBlank()) {
                    Text(text = correo, fontSize = 13.sp, color = uiColors.textSecondary)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- Entrenamiento ----------------------------------------------
            SeccionAjustes("Entrenamiento", Icons.Default.DirectionsRun, uiColors) {
                FilaNavegacion(
                    titulo = "Datos corporales",
                    valor = if (perfilFisico.estaCompleto) {
                        "${perfilFisico.estaturaCm} cm · ${perfilFisico.pesoKg.toInt()} kg · ${perfilFisico.objetivo.etiqueta}"
                    } else {
                        "Sin completar"
                    },
                    icono = Icons.Default.MonitorWeight,
                    uiColors = uiColors,
                    onClick = { navController.navigate("datos_corporales") }
                )

                DivisorSuave(uiColors)

                Text(
                    text = "Unidad de distancia",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = uiColors.textPrimary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                SelectorSegmentado(
                    opciones = listOf("Kilómetros" to "kilometros", "Millas" to "millas"),
                    valorActual = settings.unidadPrincipal,
                    uiColors = uiColors,
                    onSeleccion = { actualizarConfiguracion(settings.copy(unidadPrincipal = it)) }
                )

                DivisorSuave(uiColors)

                AjusteInterruptor(
                    titulo = "Alertas de ritmo",
                    descripcion = "Avisa cuando tu ritmo suba o baje demasiado.",
                    activo = settings.alertasRitmo,
                    uiColors = uiColors,
                    onCambio = { actualizarConfiguracion(settings.copy(alertasRitmo = it)) }
                )
            }

            // --- Inteligencia artificial ------------------------------------
            SeccionAjustes("Inteligencia artificial", Icons.Default.AutoAwesome, uiColors) {
                AjusteInterruptor(
                    titulo = "Análisis automático",
                    descripcion = if (settings.prediccionIAActiva) {
                        "Analiza cada entrenamiento al terminarlo."
                    } else {
                        "Apagado en el teléfono y en el reloj."
                    },
                    activo = settings.prediccionIAActiva,
                    uiColors = uiColors,
                    onCambio = { actualizarConfiguracion(settings.copy(prediccionIAActiva = it)) }
                )

                DivisorSuave(uiColors)

                AjusteInterruptor(
                    titulo = "Mostrar probabilidades",
                    descripcion = "Detalle de qué tan seguro está el modelo de cada ritmo.",
                    activo = settings.mostrarProbabilidades,
                    habilitado = settings.prediccionIAActiva,
                    uiColors = uiColors,
                    onCambio = { actualizarConfiguracion(settings.copy(mostrarProbabilidades = it)) }
                )

                DivisorSuave(uiColors)

                AjusteInterruptor(
                    titulo = "Recordar la última corrida",
                    descripcion = "Guarda la sesión más reciente para volver a analizarla.",
                    activo = settings.guardarUltimaCorrida,
                    habilitado = settings.prediccionIAActiva,
                    uiColors = uiColors,
                    onCambio = { actualizarConfiguracion(settings.copy(guardarUltimaCorrida = it)) }
                )
            }

            // --- Apariencia --------------------------------------------------
            SeccionAjustes("Apariencia", Icons.Default.Palette, uiColors) {
                AjusteInterruptor(
                    titulo = "Tema oscuro",
                    descripcion = "Colores suaves para entrenar de noche.",
                    activo = settings.temaOscuro,
                    uiColors = uiColors,
                    onCambio = { oscuro ->
                        actualizarConfiguracion(settings.copy(temaOscuro = oscuro))
                        onThemeChange(oscuro)
                    }
                )
            }

            // --- Reloj -------------------------------------------------------
            SeccionAjustes("Reloj", Icons.Default.Watch, uiColors) {
                val relojes by rememberRelojesVinculados(refrescosReloj)
                val vinculado = relojes.firstOrNull()
                val enviandoDatos = datosReloj.timestamp > 0L

                // Tres situaciones distintas que conviene no confundir:
                // sin reloj vinculado, vinculado pero sin datos de ALYRA, y
                // funcionando. Cada una necesita una accion diferente.
                val colorEstado = when {
                    vinculado == null -> uiColors.warning
                    enviandoDatos -> TealPrimary
                    else -> uiColors.warning
                }

                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(colorEstado, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = vinculado?.nombre ?: "Sin reloj vinculado",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = uiColors.textPrimary
                        )
                        Text(
                            text = when {
                                vinculado == null ->
                                    "Vincula tu reloj desde Galaxy Wearable o Wear OS."
                                enviandoDatos ->
                                    "Enviando datos a ALYRA."
                                vinculado.cerca ->
                                    "Conectado. Abre ALYRA en el reloj para empezar."
                                else ->
                                    "Vinculado, pero sin conexión directa por Bluetooth."
                            },
                            fontSize = 12.sp,
                            color = uiColors.textMuted
                        )
                    }
                }

                DivisorSuave(uiColors)

                FilaAccion(
                    titulo = if (vinculado == null) "Vincular un reloj" else "Administrar mi reloj",
                    descripcion = "Abre la app donde se emparejan los relojes.",
                    uiColors = uiColors,
                    onClick = {
                        // Si no hay app complementaria instalada, al menos se
                        // lleva al usuario a Bluetooth en lugar de no hacer nada.
                        if (!abrirAppDelReloj(context)) abrirAjustesBluetooth(context)
                    }
                )

                DivisorSuave(uiColors)

                FilaAccion(
                    titulo = "Ajustes de Bluetooth",
                    descripcion = "Útil si el reloj ya está emparejado pero se desconectó.",
                    uiColors = uiColors,
                    onClick = { abrirAjustesBluetooth(context) }
                )

                DivisorSuave(uiColors)

                FilaAccion(
                    titulo = "Buscar de nuevo",
                    descripcion = "Vuelve a consultar qué relojes reconoce el sistema.",
                    uiColors = uiColors,
                    onClick = { refrescosReloj++ }
                )
            }

            // --- Acerca de ---------------------------------------------------
            SeccionAjustes("Acerca de", Icons.Default.Info, uiColors) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "ALYRA · Entrenador inteligente de ritmo",
                        fontSize = 13.sp,
                        color = uiColors.textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "v1.0",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = uiColors.textMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = { cerrarSesion() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = uiColors.dangerButton, contentColor = Color.White)
            ) { Text("Cerrar sesión", fontWeight = FontWeight.Bold) }

            Spacer(modifier = Modifier.navigationBarsPadding().height(24.dp))
        }
    }
}

/**
 * Bloque de ajustes con el titulo fuera de la tarjeta, para que las secciones se
 * distingan al recorrer la pantalla.
 */
@Composable
private fun SeccionAjustes(
    titulo: String,
    icono: ImageVector,
    uiColors: AppUiColors,
    contenido: @Composable ColumnScope.() -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    ) {
        Icon(
            imageVector = icono,
            // El titulo que sigue ya nombra la seccion.
            contentDescription = null,
            tint = TealPrimary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = titulo.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = uiColors.textSecondary
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(uiColors.card, RoundedCornerShape(18.dp))
            .border(1.dp, uiColors.border.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        contenido()
    }

    Spacer(modifier = Modifier.height(20.dp))
}

@Composable
private fun DivisorSuave(uiColors: AppUiColors) {
    HorizontalDivider(
        color = uiColors.border.copy(alpha = 0.35f),
        thickness = 0.5.dp
    )
}

@Composable
private fun AjusteInterruptor(
    titulo: String,
    descripcion: String,
    activo: Boolean,
    uiColors: AppUiColors,
    habilitado: Boolean = true,
    onCambio: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titulo,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (habilitado) uiColors.textPrimary else uiColors.textMuted
            )
            Text(text = descripcion, fontSize = 12.sp, color = uiColors.textMuted)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = activo,
            enabled = habilitado,
            onCheckedChange = onCambio,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = TealPrimary,
                uncheckedThumbColor = uiColors.textMuted,
                uncheckedTrackColor = uiColors.cardSecondary
            )
        )
    }
}

/**
 * Fila que ejecuta una accion, sin llevar a otra pantalla de la aplicacion.
 * Se distingue de [FilaNavegacion] en que no muestra la flecha de avance, para
 * no prometer una navegacion que no ocurre.
 */
@Composable
private fun FilaAccion(
    titulo: String,
    descripcion: String,
    uiColors: AppUiColors,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp)
    ) {
        Text(
            text = titulo,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = TealPrimary
        )
        Text(text = descripcion, fontSize = 12.sp, color = uiColors.textMuted)
    }
}

/** Fila que lleva a otra pantalla, con el valor actual como resumen. */
@Composable
private fun FilaNavegacion(
    titulo: String,
    valor: String,
    icono: ImageVector,
    uiColors: AppUiColors,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icono,
            contentDescription = null,
            tint = TealPrimary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = titulo, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = uiColors.textPrimary)
            Text(text = valor, fontSize = 12.sp, color = uiColors.textMuted)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = uiColors.textMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Selector de dos o mas opciones en una sola fila. Ocupa la mitad que unos
 * botones de radio apilados y la opcion activa se distingue de inmediato.
 */
@Composable
private fun SelectorSegmentado(
    opciones: List<Pair<String, String>>,
    valorActual: String,
    uiColors: AppUiColors,
    onSeleccion: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(uiColors.cardSecondary, RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        opciones.forEach { (etiqueta, valor) ->
            OpcionSegmento(
                etiqueta = etiqueta,
                seleccionada = valorActual == valor,
                uiColors = uiColors,
                onClick = { onSeleccion(valor) }
            )
        }
    }
}

@Composable
private fun RowScope.OpcionSegmento(
    etiqueta: String,
    seleccionada: Boolean,
    uiColors: AppUiColors,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .background(
                if (seleccionada) TealPrimary else Color.Transparent,
                RoundedCornerShape(9.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = etiqueta,
            fontSize = 14.sp,
            fontWeight = if (seleccionada) FontWeight.Bold else FontWeight.Normal,
            color = if (seleccionada) Color.White else uiColors.textSecondary
        )
    }
}

fun limpiarSesionLocal(context: Context) {
    listOf("session", "app_prefs").forEach { nombre ->
        context.getSharedPreferences(nombre, Context.MODE_PRIVATE).edit().clear().apply()
    }
    SessionManager(context).logout()
}
