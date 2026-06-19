package com.example.myapplication1.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AlyraLightColorScheme = lightColorScheme(
    primary          = AlyraBlue100,
    onPrimary        = LightSurface,
    primaryContainer = AlyraBlue20,
    onPrimaryContainer = LightTextPrimary,

    secondary        = AlyraBlue80,
    onSecondary      = LightSurface,
    secondaryContainer = AlyraBlue40,
    onSecondaryContainer = LightTextPrimary,

    tertiary         = AlyraBlue60,
    onTertiary       = LightSurface,

    background       = LightBackground,
    onBackground     = LightTextPrimary,

    surface          = LightSurface,
    onSurface        = LightTextPrimary,
    surfaceVariant   = LightSurface2,
    onSurfaceVariant = LightTextSecondary,

    outline          = LightBorder,
    error            = ColorDanger,
)

private val AlyradarkColorScheme = darkColorScheme(
    primary          = AlyraBlue80,
    onPrimary        = DarkBackground,
    primaryContainer = AlyraBlue100,
    onPrimaryContainer = DarkTextPrimary,

    secondary        = AlyraBlue60,
    onSecondary      = DarkBackground,
    secondaryContainer = DarkSurface2,
    onSecondaryContainer = DarkTextPrimary,

    tertiary         = AlyraBlue40,
    onTertiary       = DarkBackground,

    background       = DarkBackground,
    onBackground     = DarkTextPrimary,

    surface          = DarkSurface,
    onSurface        = DarkTextPrimary,
    surfaceVariant   = DarkSurface2,
    onSurfaceVariant = DarkTextSecondary,

    outline          = DarkBorder,
    error            = ColorDanger,
)

@Composable
fun MyApplication1Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) AlyradarkColorScheme else AlyraLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}