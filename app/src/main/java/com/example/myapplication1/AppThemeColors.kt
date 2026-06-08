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
            background = Color(0xFF101114),
            card = Color(0xFF1A1C20),
            cardSecondary = Color(0xFF23262B),
            textPrimary = Color(0xFFF5F5F5),
            textSecondary = Color(0xFFB8BCC4),
            textMuted = Color(0xFF8B909A),
            border = Color(0xFF30343A),
            bottomBar = Color(0xFF17191D),
            bottomSelected = Color(0xFF2F4F6F),
            bottomUnselected = Color(0xFFB8BCC4),
            primaryButton = Color(0xFF90CAF9),
            primaryButtonText = Color(0xFF102030),
            dangerButton = Color(0xFFD9534F),
            success = Color(0xFF4CAF50),
            warning = Color(0xFFFFB74D),
            inputBackground = Color(0xFF24272D),
            mapPanel = Color(0xFF2A2D33)
        )
    } else {
        AppUiColors(
            background = Color(0xFFF2F2F2),
            card = Color.White,
            cardSecondary = Color(0xFFF7F8FC),
            textPrimary = Color(0xFF202020),
            textSecondary = Color(0xFF606060),
            textMuted = Color.Gray,
            border = Color(0xFFE6E8EF),
            bottomBar = Color(0xFFF4F6FA),
            bottomSelected = Color(0xFFDCEEFF),
            bottomUnselected = Color(0xFF404852),
            primaryButton = Color(0xFF3A6EA5),
            primaryButtonText = Color.White,
            dangerButton = Color(0xFFD9534F),
            success = Color(0xFF2ECC71),
            warning = Color(0xFFFFB74D),
            inputBackground = Color.White,
            mapPanel = Color(0xFFE6E8EF)
        )
    }
}