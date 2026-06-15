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
    val mapPanel: Color,

    val topBarStart: Color,
    val topBarEnd: Color,

    val mapPolyline: Color,
    val mapMarkerStart: Color,
    val mapMarkerEnd: Color,
)

fun appUiColors(temaOscuro: Boolean): AppUiColors =
    if (temaOscuro) darkAlyraColors() else lightAlyraColors()

private fun lightAlyraColors() = AppUiColors(

    background    = Color(0xFFF4F8FC),
    card          = Color(0xFFFFFFFF),
    cardSecondary = Color(0xFFEDF5FC),


    textPrimary   = Color(0xFF0D1B2A),
    textSecondary = Color(0xFF4A6177),
    textMuted     = Color(0xFF8FA8BE),

    border          = Color(0xFFD0E8F7),
    bottomBar       = Color(0xFFFFFFFF),
    bottomSelected  = Color(0xFFD6EFFF),
    bottomUnselected= Color(0xFF8FA8BE),

    primaryButton     = Color(0xFF008BD9),
    primaryButtonText = Color(0xFFFFFFFF),
    dangerButton      = Color(0xFFE53935),

    success = Color(0xFF2ECC71),
    warning = Color(0xFFFFB74D),

    inputBackground = Color(0xFFFFFFFF),
    mapPanel        = Color(0xFFEDF5FC),

    topBarStart = Color(0xFF008BD9),
    topBarEnd   = Color(0xFF3EAFF4),

    mapPolyline   = Color(0xFF008BD9),
    mapMarkerStart= Color(0xFF2ECC71),
    mapMarkerEnd  = Color(0xFFE53935),
)

private fun darkAlyraColors() = AppUiColors(
    background    = Color(0xFF0A1520),
    card          = Color(0xFF112030),
    cardSecondary = Color(0xFF172C40),

    textPrimary   = Color(0xFFE8F4FD),
    textSecondary = Color(0xFF7FB3D3),
    textMuted     = Color(0xFF4A7A9B),

    border          = Color(0xFF1E3D55),
    bottomBar       = Color(0xFF0D1C2E),
    bottomSelected  = Color(0xFF1A3A55),
    bottomUnselected= Color(0xFF4A7A9B),

    primaryButton     = Color(0xFF069AF1),
    primaryButtonText = Color(0xFF0A1520),
    dangerButton      = Color(0xFFE53935),

    success = Color(0xFF2ECC71),
    warning = Color(0xFFFFB74D),

    inputBackground = Color(0xFF172C40),
    mapPanel        = Color(0xFF112030),

    topBarStart = Color(0xFF0A1520),
    topBarEnd   = Color(0xFF0E2A42),

    mapPolyline   = Color(0xFF069AF1),
    mapMarkerStart= Color(0xFF2ECC71),
    mapMarkerEnd  = Color(0xFFE53935),
)