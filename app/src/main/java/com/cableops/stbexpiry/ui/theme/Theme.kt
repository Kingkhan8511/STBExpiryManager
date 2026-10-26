package com.cableops.stbexpiry.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Brand palette ─────────────────────────────────────────────────────────────────────
val CablePrimary        = Color(0xFF1565C0)   // Deep Cable Blue
val CablePrimaryVariant = Color(0xFF003C8F)
val CableSecondary      = Color(0xFF00897B)   // Teal accent
val CableSurface        = Color(0xFFF5F5F5)
val CableBackground     = Color(0xFFFFFFFF)

// Status badge colours – shared with UI components
val BadgeRed    = Color(0xFFD32F2F)
val BadgeYellow = Color(0xFFF57F17)
val BadgeGreen  = Color(0xFF2E7D32)

private val LightColorScheme = lightColorScheme(
    primary          = CablePrimary,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFD0E4FF),
    secondary        = CableSecondary,
    onSecondary      = Color.White,
    background       = CableBackground,
    surface          = CableSurface,
    onBackground     = Color(0xFF1C1B1F),
    onSurface        = Color(0xFF1C1B1F),
)

private val DarkColorScheme = darkColorScheme(
    primary          = Color(0xFF90CAF9),
    onPrimary        = Color(0xFF003063),
    primaryContainer = Color(0xFF00468B),
    secondary        = Color(0xFF80CBC4),
    onSecondary      = Color(0xFF00382F),
    background       = Color(0xFF1C1B1F),
    surface          = Color(0xFF2B2930),
    onBackground     = Color(0xFFE6E1E5),
    onSurface        = Color(0xFFE6E1E5),
)

@Composable
fun STBExpiryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

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
        content     = content
    )
}
