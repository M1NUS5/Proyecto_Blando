package com.example.myapplication1

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

private val TealPrimary = Color(0xFF26A69A)
private val TealMedium = Color(0xFF4DB6AC)

/**
 * Captura de estatura, peso, edad, sexo, nivel de actividad y objetivo.
 *
 * Todos los campos son necesarios para calcular las metas: la ecuacion de
 * Mifflin-St Jeor no funciona sin sexo ni edad, y el gasto diario no se puede
 * estimar sin el nivel de actividad. Por eso la pantalla muestra el resultado
 * en vivo solo cuando el conjunto esta completo.
 */
@Composable
fun DatosCorporalesScreen(navController: NavController) {
    val context = LocalContext.current

    val settings by AppSettingsStore.settings.collectAsState()
    val uiColors = appUiColors(settings.temaOscuro)
    val dimens = rememberResponsiveDimens()

    val perfilGuardado by PerfilStore.fisico.collectAsState()

    // Los campos numericos se editan como texto para que el usuario pueda
    // borrarlos por completo; convertirlos a numero en cada pulsacion haria que
    // un campo vacio se volviera "0" y fuera imposible de limpiar.
    var edad by remember { mutableStateOf(perfilGuardado.edad.takeIf { it > 0 }?.toString() ?: "") }
    var estatura by remember { mutableStateOf(perfilGuardado.estaturaCm.takeIf { it > 0 }?.toString() ?: "") }
    var peso by remember {
        mutableStateOf(
            perfilGuardado.pesoKg.takeIf { it > 0 }?.let { valor ->
                if (valor % 1.0 == 0.0) valor.toInt().toString() else valor.toString()
            } ?: ""
        )
    }
    var sexo by remember { mutableStateOf(perfilGuardado.sexo) }
    var actividad by remember { mutableStateOf(perfilGuardado.nivelActividad) }
    var objetivo by remember { mutableStateOf(perfilGuardado.objetivo) }

    var guardado by remember { mutableStateOf(false) }

    val perfilEnEdicion = PerfilFisico(
        edad = edad.toIntOrNull() ?: 0,
        estaturaCm = estatura.toIntOrNull() ?: 0,
        pesoKg = peso.replace(',', '.').toDoubleOrNull() ?: 0.0,
        sexo = sexo,
        nivelActividad = actividad,
        objetivo = objetivo
    )

    val metas = perfilEnEdicion.calcularMetas()

    Box(modifier = Modifier.fillMaxSize().background(uiColors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // Encabezado
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(TealPrimary, TealMedium)))
                    .padding(horizontal = 16.dp, vertical = 18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White,
                        modifier = Modifier
                            .size(26.dp)
                            .clickable { navController.popBackStack() }
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Datos corporales",
                            fontSize = dimens.titleFontSize,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Para calcular tus metas nutricionales",
                            fontSize = dimens.labelFontSize,
                            color = Color(0xFFE0F2F1)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.horizontalPadding)
                    .padding(top = 18.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // --- Medidas ---
                SeccionDatos(titulo = "Tus medidas", uiColors = uiColors, dimens = dimens) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CampoNumerico(
                            valor = edad,
                            onValorChange = { edad = it.filter { c -> c.isDigit() }.take(3); guardado = false },
                            etiqueta = "Edad",
                            sufijo = "años",
                            uiColors = uiColors,
                            modifier = Modifier.weight(1f)
                        )
                        CampoNumerico(
                            valor = estatura,
                            onValorChange = { estatura = it.filter { c -> c.isDigit() }.take(3); guardado = false },
                            etiqueta = "Estatura",
                            sufijo = "cm",
                            uiColors = uiColors,
                            modifier = Modifier.weight(1f)
                        )
                        CampoNumerico(
                            valor = peso,
                            onValorChange = { nuevo ->
                                // Un solo separador decimal, punto o coma.
                                peso = nuevo.filter { c -> c.isDigit() || c == '.' || c == ',' }
                                    .replace(',', '.')
                                    .let { texto ->
                                        val partes = texto.split('.')
                                        if (partes.size > 2) partes[0] + "." + partes[1] else texto
                                    }
                                    .take(5)
                                guardado = false
                            },
                            etiqueta = "Peso",
                            sufijo = "kg",
                            uiColors = uiColors,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Sexo",
                        fontSize = dimens.labelFontSize,
                        fontWeight = FontWeight.Bold,
                        color = uiColors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Sexo.values().forEach { opcion ->
                            ChipSeleccion(
                                texto = opcion.etiqueta,
                                seleccionado = sexo == opcion,
                                uiColors = uiColors,
                                dimens = dimens,
                                modifier = Modifier.weight(1f),
                                onClick = { sexo = opcion; guardado = false }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "La fórmula médica que calcula tu metabolismo usa un valor " +
                                "distinto según el sexo, por eso se pide.",
                        fontSize = dimens.labelFontSize,
                        color = uiColors.textMuted
                    )
                }

                // --- Actividad ---
                SeccionDatos(titulo = "¿Qué tan activo eres?", uiColors = uiColors, dimens = dimens) {
                    NivelActividad.values().forEach { opcion ->
                        OpcionLista(
                            titulo = opcion.etiqueta,
                            descripcion = opcion.descripcion,
                            seleccionado = actividad == opcion,
                            uiColors = uiColors,
                            dimens = dimens,
                            onClick = { actividad = opcion; guardado = false }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // --- Objetivo ---
                SeccionDatos(titulo = "Tu objetivo", uiColors = uiColors, dimens = dimens) {
                    ObjetivoPeso.values().forEach { opcion ->
                        OpcionLista(
                            titulo = opcion.etiqueta,
                            descripcion = opcion.descripcion,
                            seleccionado = objetivo == opcion,
                            uiColors = uiColors,
                            dimens = dimens,
                            onClick = { objetivo = opcion; guardado = false }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // --- Vista previa de las metas ---
                if (metas != null) {
                    TarjetaMetas(metas = metas, uiColors = uiColors, dimens = dimens)
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(uiColors.cardSecondary, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Completa tu edad, estatura y peso para ver tus metas diarias.",
                            fontSize = dimens.bodyFontSize,
                            color = uiColors.textMuted
                        )
                    }
                }

                Button(
                    onClick = {
                        PerfilStore.actualizarFisico(context, perfilEnEdicion)
                        guardado = true
                    },
                    enabled = metas != null && !guardado,
                    modifier = Modifier.fillMaxWidth().height(dimens.buttonHeight),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TealPrimary,
                        contentColor = Color.White,
                        disabledContainerColor = uiColors.border
                    )
                ) {
                    if (guardado) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (guardado) "Datos guardados" else "Guardar mis datos",
                        fontWeight = FontWeight.Bold,
                        fontSize = dimens.buttonFontSize
                    )
                }

                Text(
                    text = "Estas cifras son una orientación general basada en fórmulas " +
                            "estándar. No sustituyen la valoración de un nutriólogo o un médico.",
                    fontSize = dimens.labelFontSize,
                    color = uiColors.textMuted
                )
            }
        }
    }
}

@Composable
private fun SeccionDatos(
    titulo: String,
    uiColors: AppUiColors,
    dimens: ResponsiveDimens,
    contenido: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(uiColors.card, RoundedCornerShape(18.dp))
            .border(1.dp, uiColors.border, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Text(
            text = titulo,
            fontSize = dimens.bodyFontSize,
            fontWeight = FontWeight.Bold,
            color = uiColors.textPrimary
        )
        Spacer(modifier = Modifier.height(14.dp))
        contenido()
    }
}

@Composable
private fun CampoNumerico(
    valor: String,
    onValorChange: (String) -> Unit,
    etiqueta: String,
    sufijo: String,
    uiColors: AppUiColors,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = valor,
            onValueChange = onValorChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text(etiqueta, fontSize = 12.sp) },
            suffix = { Text(sufijo, fontSize = 12.sp, color = uiColors.textMuted) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TealPrimary,
                unfocusedBorderColor = uiColors.border,
                focusedLabelColor = TealPrimary,
                unfocusedLabelColor = uiColors.textMuted,
                focusedTextColor = uiColors.textPrimary,
                unfocusedTextColor = uiColors.textPrimary,
                focusedContainerColor = uiColors.inputBackground,
                unfocusedContainerColor = uiColors.inputBackground
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ChipSeleccion(
    texto: String,
    seleccionado: Boolean,
    uiColors: AppUiColors,
    dimens: ResponsiveDimens,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                if (seleccionado) TealPrimary else uiColors.inputBackground,
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                if (seleccionado) TealPrimary else uiColors.border,
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto,
            fontSize = dimens.bodyFontSize,
            fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Normal,
            color = if (seleccionado) Color.White else uiColors.textPrimary
        )
    }
}

@Composable
private fun OpcionLista(
    titulo: String,
    descripcion: String,
    seleccionado: Boolean,
    uiColors: AppUiColors,
    dimens: ResponsiveDimens,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (seleccionado) TealPrimary.copy(alpha = 0.15f) else uiColors.inputBackground,
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                if (seleccionado) TealPrimary else uiColors.border,
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titulo,
                fontSize = dimens.bodyFontSize,
                fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Medium,
                color = uiColors.textPrimary
            )
            Text(
                text = descripcion,
                fontSize = dimens.labelFontSize,
                color = uiColors.textMuted
            )
        }

        if (seleccionado) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = TealPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Vista previa en vivo de lo que el usuario obtendra al guardar. */
@Composable
private fun TarjetaMetas(
    metas: MetasNutricionales,
    uiColors: AppUiColors,
    dimens: ResponsiveDimens
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(uiColors.card, RoundedCornerShape(18.dp))
            .border(1.5.dp, TealPrimary, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "Tus metas diarias",
            fontSize = dimens.bodyFontSize,
            fontWeight = FontWeight.Bold,
            color = TealPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "${metas.caloriasMeta}",
                fontSize = dimens.titleFontSize,
                fontWeight = FontWeight.Bold,
                color = uiColors.textPrimary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "kcal al día",
                fontSize = dimens.bodyFontSize,
                color = uiColors.textSecondary,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = uiColors.border, thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MetaMacro("Proteína", metas.proteinaMeta, uiColors, dimens)
            MetaMacro("Carbos", metas.carbosMeta, uiColors, dimens)
            MetaMacro("Grasas", metas.grasasMeta, uiColors, dimens)
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = uiColors.border, thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(14.dp))

        FilaDato("Metabolismo en reposo", "${metas.metabolismoBasal} kcal", uiColors, dimens)
        Spacer(modifier = Modifier.height(6.dp))
        FilaDato("Gasto con tu actividad", "${metas.gastoDiario} kcal", uiColors, dimens)
        Spacer(modifier = Modifier.height(6.dp))
        FilaDato(
            etiqueta = "Índice de masa corporal",
            valor = "${String.format("%.1f", metas.imc)} · ${metas.categoriaImc}",
            uiColors = uiColors,
            dimens = dimens
        )

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "El índice de masa corporal no distingue músculo de grasa, así que " +
                    "en personas muy entrenadas puede salir alto sin que signifique algo malo.",
            fontSize = dimens.labelFontSize,
            color = uiColors.textMuted
        )
    }
}

@Composable
private fun MetaMacro(
    etiqueta: String,
    gramos: Int,
    uiColors: AppUiColors,
    dimens: ResponsiveDimens
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${gramos}g",
            fontSize = dimens.statValueSize,
            fontWeight = FontWeight.Bold,
            color = uiColors.textPrimary
        )
        Text(
            text = etiqueta,
            fontSize = dimens.statLabelSize,
            color = uiColors.textSecondary
        )
    }
}

@Composable
private fun FilaDato(
    etiqueta: String,
    valor: String,
    uiColors: AppUiColors,
    dimens: ResponsiveDimens
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = etiqueta, fontSize = dimens.labelFontSize, color = uiColors.textSecondary)
        Text(
            text = valor,
            fontSize = dimens.labelFontSize,
            fontWeight = FontWeight.Bold,
            color = uiColors.textPrimary
        )
    }
}
