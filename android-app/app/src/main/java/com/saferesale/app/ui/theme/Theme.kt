package com.saferesale.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Dark Color Scheme ──────────────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary             = ElectricViolet,
    onPrimary           = DarkBackground,
    primaryContainer    = ElectricVioletDim,
    onPrimaryContainer  = Color(0xFFD8D6FF),
    secondary           = TealAccent,
    onSecondary         = DarkBackground,
    secondaryContainer  = TealDim,
    onSecondaryContainer= Color(0xFFB2F5EA),
    tertiary            = AmberWarm,
    onTertiary          = DarkBackground,
    tertiaryContainer   = Color(0xFF7A4F00),
    onTertiaryContainer = Color(0xFFFFDDB3),
    error               = CoralRed,
    onError             = DarkBackground,
    errorContainer      = Color(0xFF93000A),
    onErrorContainer    = Color(0xFFFFDAD6),
    background          = DarkBackground,
    onBackground        = DarkOnSurface,
    surface             = DarkSurface,
    onSurface           = DarkOnSurface,
    surfaceVariant      = DarkSurfaceVar,
    onSurfaceVariant    = Color(0xFFB0B0CC),
    outline             = DarkOutline,
    outlineVariant      = DarkOutlineVar,
    scrim               = Color(0xFF000000),
    inverseSurface      = DarkOnSurface,
    inverseOnSurface    = DarkSurface,
    inversePrimary      = ElectricVioletDim,
)

// ── Light Color Scheme ─────────────────────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary             = ElectricVioletDim,
    onPrimary           = LightBackground,
    primaryContainer    = Color(0xFFE6E4FF),
    onPrimaryContainer  = Color(0xFF1A1166),
    secondary           = TealDim,
    onSecondary         = LightBackground,
    secondaryContainer  = Color(0xFFB2F5EA),
    onSecondaryContainer= Color(0xFF003D33),
    tertiary            = Color(0xFFB06000),
    onTertiary          = LightBackground,
    tertiaryContainer   = Color(0xFFFFDDB3),
    onTertiaryContainer = Color(0xFF3A1C00),
    error               = Color(0xFFBA1A1A),
    onError             = LightBackground,
    errorContainer      = Color(0xFFFFDAD6),
    onErrorContainer    = Color(0xFF410002),
    background          = LightBackground,
    onBackground        = LightOnSurface,
    surface             = LightSurface,
    onSurface           = LightOnSurface,
    surfaceVariant      = LightSurfaceVar,
    onSurfaceVariant    = Color(0xFF4A4A6A),
    outline             = LightOutline,
    outlineVariant      = LightOutlineVar,
    scrim               = Color(0xFF000000),
    inverseSurface      = LightOnSurface,
    inverseOnSurface    = LightSurface,
    inversePrimary      = ElectricViolet,
)

val LocalIsDarkTheme = compositionLocalOf { true }

@Composable
fun CoreVTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = CoreVTypography,
        shapes      = CoreVShapes,
        content     = content
    )
}
