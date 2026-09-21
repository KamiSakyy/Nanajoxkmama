package com.kamisakyy.nanajoxkmama.core.design

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/* ============ design tokens — black-first, website palette ============ */
object AppColors {
    val Bg = Color(0xFF000000)
    val S1 = Color(0xFF0B0B0C)
    val S2 = Color(0xFF141416)
    val S3 = Color(0xFF1E1E21)
    val S4 = Color(0xFF28282C)
    val S5 = Color(0xFF333338)
    val On = Color(0xFFFFFFFF)
    val Var = Color(0xFF9A9AA2)
    val Dim = Color(0xFF63636B)
    val Accent = Color(0xFF8AB4F8)
    val TagOp = Color(0xFF8AB4F8)
    val TagEd = Color(0xFF8E8E96)
    val TagIn = Color(0xFF7EE0C0)
}

object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

private val DarkScheme = darkColorScheme(
    primary = AppColors.Accent,
    onPrimary = Color(0xFF0B0B0C),
    secondary = AppColors.S5,
    background = AppColors.Bg,
    onBackground = AppColors.On,
    surface = AppColors.Bg,
    onSurface = AppColors.On,
    surfaceVariant = AppColors.S2,
    onSurfaceVariant = AppColors.Var,
    surfaceContainer = AppColors.S1,
    surfaceContainerHigh = AppColors.S2,
    surfaceContainerHighest = AppColors.S3,
    surfaceBright = AppColors.S4,
    surfaceDim = AppColors.Bg,
    outline = AppColors.S5,
    outlineVariant = AppColors.S3,
    error = Color(0xFFFF6B6B),
    scrim = Color(0xCC000000),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3B6FD4),
    onPrimary = Color.White,
    secondary = Color(0xFFE3E3E8),
    background = Color(0xFFF6F6F8),
    onBackground = Color(0xFF0B0B0C),
    surface = Color(0xFFF6F6F8),
    onSurface = Color(0xFF0B0B0C),
    surfaceVariant = Color(0xFFEAEAEE),
    onSurfaceVariant = Color(0xFF5A5A62),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0F0F4),
    surfaceContainerHighest = Color(0xFFE7E7EC),
    outline = Color(0xFFC8C8CE),
    outlineVariant = Color(0xFFDEDEE3),
    error = Color(0xFFD33434),
)

@Composable
fun AniBeatTheme(
    themeMode: Int = 0, // 0 system, 1 dark, 2 light
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        1 -> true
        2 -> false
        else -> systemDark
    }
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
