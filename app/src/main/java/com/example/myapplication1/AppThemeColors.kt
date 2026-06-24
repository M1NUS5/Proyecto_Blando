package com.example.myapplication1

import androidx.compose.ui.graphics.Color

data class AppUiColors(
    val background: Color,
    val card: Color,
    val cardSecondary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val border: Color,
    val bottomBar: Color,
    val bottomSelected: Color,
    val bottomUnselected: Color,
    val primaryButton: Color,
    val primaryButtonText: Color,
    val dangerButton: Color,
    val success: Color,
    val warning: Color,
    val inputBackground: Color,
    val mapPanel: Color
)


fun appUiColors(
    temaOscuro: Boolean
): AppUiColors {
    return if (temaOscuro) {
        AppUiColors(
            background = Color(0xFF0D1F1E),
            card = Color(0xFF122120),
            cardSecondary = Color(0xFF1A2E2D),
            textPrimary = Color(0xFFF5F5F5),
            textSecondary = Color(0xFFB2DFDB),
            textMuted = Color(0xFF80CBC4),
            border = Color(0xFF26A69A),
            bottomBar = Color(0xFF0D1F1E),
            bottomSelected = Color(0xFF26A69A),
            bottomUnselected = Color(0xFF80CBC4),
            primaryButton = Color(0xFF26A69A),
            primaryButtonText = Color(0xFFFFFFFF),
            dangerButton = Color(0xFFD9534F),
            success = Color(0xFF4DB6AC),
            warning = Color(0xFFFFB74D),
            inputBackground = Color(0xFF1A2E2D),
            mapPanel = Color(0xFF122120)
        )
    } else {
        AppUiColors(
            background = Color(0xFFE0F2F1),
            card = Color(0xFFFFFFFF),
            cardSecondary = Color(0xFFB2DFDB),
            textPrimary = Color(0xFF1A2E2D),
            textSecondary = Color(0xFF26A69A),
            textMuted = Color(0xFF4DB6AC),
            border = Color(0xFF80CBC4),
            bottomBar = Color(0xFFFFFFFF),
            bottomSelected = Color(0xFFB2DFDB),
            bottomUnselected = Color(0xFF4DB6AC),
            primaryButton = Color(0xFF26A69A),
            primaryButtonText = Color(0xFFFFFFFF),
            dangerButton = Color(0xFFD9534F),
            success = Color(0xFF26A69A),
            warning = Color(0xFFFFB74D),
            inputBackground = Color(0xFFFFFFFF),
            mapPanel = Color(0xFFB2DFDB)
        )
    }
}