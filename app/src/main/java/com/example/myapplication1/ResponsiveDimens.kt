package com.example.myapplication1

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ResponsiveDimens(
    val screenWidthDp: Float,
    val screenHeightDp: Float,
    val fontScale: Float = 1f
) {
    private val effectiveWidth: Float get() = screenWidthDp / fontScale.coerceAtLeast(0.85f)

    private val isCompact: Boolean  get() = effectiveWidth < 340f
    private val isSmall: Boolean    get() = effectiveWidth in 340f..374f
    private val isMedium: Boolean   get() = effectiveWidth in 375f..420f
    private val isLarge: Boolean    get() = effectiveWidth > 420f

    val topBarHeight: Dp        get() = when { isCompact -> 48.dp; isSmall -> 54.dp; isMedium -> 58.dp; else -> 64.dp }
    val topBarLogoSize: Dp      get() = when { isCompact -> 22.dp; isSmall -> 26.dp; isMedium -> 30.dp; else -> 36.dp }
    val mapHeight: Dp           get() = when { isCompact -> 160.dp; isSmall -> 200.dp; isMedium -> 230.dp; else -> 270.dp }
    val cardPadding: Dp         get() = when { isCompact -> 8.dp;  isSmall -> 10.dp; isMedium -> 12.dp; else -> 16.dp }
    val cardRadius: Dp          get() = when { isCompact -> 10.dp; isSmall -> 12.dp; isMedium -> 14.dp; else -> 18.dp }
    val buttonHeight: Dp        get() = when { isCompact -> 40.dp; isSmall -> 44.dp; isMedium -> 48.dp; else -> 54.dp }
    val iconSize: Dp            get() = when { isCompact -> 18.dp; isSmall -> 20.dp; isMedium -> 22.dp; else -> 26.dp }
    val avatarSize: Dp          get() = when { isCompact -> 56.dp; isSmall -> 64.dp; isMedium -> 76.dp; else -> 90.dp }
    val loginLogoSize: Dp       get() = when { isCompact -> 64.dp; isSmall -> 80.dp; isMedium -> 96.dp; else -> 112.dp }
    val calendarDayFont: TextUnit get() = when { isCompact -> 10.sp; isSmall -> 11.sp; isMedium -> 13.sp; else -> 14.sp }
    val bottomNavHeight: Dp     get() = when { isCompact -> 52.dp; else -> 64.dp }
    val horizontalPadding: Dp   get() = when { isCompact -> 10.dp; isSmall -> 12.dp; isMedium -> 14.dp; else -> 18.dp }
    val verticalSpacing: Dp     get() = when { isCompact -> 6.dp;  isSmall -> 8.dp;  isMedium -> 10.dp; else -> 14.dp }

    val titleFontSize: TextUnit   get() = when { isCompact -> 16.sp; isSmall -> 18.sp; isMedium -> 20.sp; else -> 24.sp }
    val bodyFontSize: TextUnit    get() = when { isCompact -> 12.sp; isSmall -> 13.sp; isMedium -> 14.sp; else -> 15.sp }
    val labelFontSize: TextUnit   get() = when { isCompact -> 10.sp; isSmall -> 11.sp; isMedium -> 12.sp; else -> 13.sp }
    val statValueSize: TextUnit   get() = when { isCompact -> 16.sp; isSmall -> 18.sp; isMedium -> 20.sp; else -> 22.sp }
    val statLabelSize: TextUnit   get() = when { isCompact -> 10.sp; isSmall -> 11.sp; isMedium -> 12.sp; else -> 13.sp }
    val topBarFontSize: TextUnit  get() = when { isCompact -> 14.sp; isSmall -> 15.sp; isMedium -> 16.sp; else -> 18.sp }
    val buttonFontSize: TextUnit  get() = when { isCompact -> 13.sp; isSmall -> 14.sp; isMedium -> 14.sp; else -> 16.sp }
}

val LocalDimens = compositionLocalOf { ResponsiveDimens(375f, 812f) }

@Composable
fun rememberResponsiveDimens(): ResponsiveDimens {
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    return ResponsiveDimens(
        screenWidthDp  = config.screenWidthDp.toFloat(),
        screenHeightDp = config.screenHeightDp.toFloat(),
        fontScale      = density.fontScale
    )
}