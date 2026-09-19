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

// ── Dark Color Scheme (eclassify dark) ─────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary             = TerritoryAccent,
    onPrimary           = Color(0xFF001316),
    primaryContainer    = Color(0xFF1C6E7A),
    onPrimaryContainer  = Color(0xFFB8F2FB),
    secondary           = ForthAccent,
    onSecondary         = Color(0xFF3C0F05),
    secondaryContainer  = Color(0xFF7A4335),
    onSecondaryContainer= Color(0xFFFFDAD3),
    tertiary            = PendingBlue,
    onTertiary          = Color(0xFFFFFFFF),
    tertiaryContainer   = Color(0xFF0C5D9C),
    onTertiaryContainer = Color(0xFFD6ECff),
    error               = Color(0xFFFFB4AB),
    onError             = Color(0xFF690005),
    errorContainer      = Color(0xFF93000A),
    onErrorContainer    = Color(0xFFFFDAD6),
    background          = Color(0xFF121212),
    onBackground        = Color(0xFFFDFDFD),
    surface             = Color(0xFF1C1C1C),
    onSurface           = Color(0xFFFDFDFD),
    surfaceVariant      = Color(0xFF24242C),
    onSurfaceVariant    = Color(0xFFB0B0C0),
    outline             = Color(0xFF3A3A44),
    outlineVariant      = Color(0xFF2A2A34),
    scrim               = Color(0xFF000000),
    inverseSurface      = Color(0xFFFDFDFD),
    inverseOnSurface    = Color(0xFF1C1C1C),
    inversePrimary      = TerritoryAccent,
)

// ── Light Color Scheme (eclassify light) ───────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary             = TerritoryAccent,
    onPrimary           = Color(0xFFFFFFFF),
    primaryContainer    = Color(0x1A00B2CA),
    onPrimaryContainer  = Color(0xFF00363D),
    secondary           = ForthAccent,
    onSecondary         = Color(0xFFFFFFFF),
    secondaryContainer  = Color(0x1AFA6E53),
    onSecondaryContainer= Color(0xFF3C0F05),
    tertiary            = PendingBlue,
    onTertiary          = Color(0xFFFFFFFF),
    tertiaryContainer   = Color(0xFFD6ECff),
    onTertiaryContainer = Color(0xFF053354),
    error               = Color(0xFFBA1A1A),
    onError             = Color(0xFFFFFFFF),
    errorContainer      = Color(0xFFFFDAD6),
    onErrorContainer    = Color(0xFF410002),
    background          = LightBackground,
    onBackground        = LightOnSurface,
    surface             = LightSurface,
    onSurface           = LightOnSurface,
    surfaceVariant      = LightSurfaceVar,
    onSurfaceVariant    = LightTextAccent,
    outline             = LightOutline,
    outlineVariant      = LightOutlineVar,
    scrim               = Color(0xFF000000),
    inverseSurface      = LightOnSurface,
    inverseOnSurface    = LightSurface,
    inversePrimary      = TerritoryAccent,
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
