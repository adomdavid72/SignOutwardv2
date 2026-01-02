package com.example.signoutwardv2.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ============================================================================
// DESIGN TOKENS - Theme Configuration
// ============================================================================
// Material Design 3 Light Theme with Purple/White palette
// Optimized for tablet displays
// ============================================================================

private val SignOutwardColorScheme = lightColorScheme(
    // Primary colors
    primary = PurplePrimary,
    onPrimary = TextOnPurple,
    primaryContainer = SurfaceLight,
    onPrimaryContainer = PurplePrimaryDark,
    
    // Secondary colors
    secondary = PurpleAccent,
    onSecondary = TextOnPurple,
    secondaryContainer = SurfaceMedium,
    onSecondaryContainer = PurplePrimaryDark,
    
    // Tertiary colors
    tertiary = PurplePrimaryLight,
    onTertiary = TextPrimary,
    tertiaryContainer = OffWhite,
    onTertiaryContainer = TextPrimary,
    
    // Background and surface
    background = White,
    onBackground = TextPrimary,
    surface = White,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = TextSecondary,
    
    // Outline
    outline = BorderLight,
    outlineVariant = BorderPurple,
    
    // Error colors
    error = Error,
    onError = White,
    errorContainer = androidx.compose.ui.graphics.Color(0xFFFEE2E2),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFF991B1B),
    
    // Inverse colors
    inverseSurface = TextPrimary,
    inverseOnSurface = White,
    inversePrimary = PurplePrimaryLight,
    
    // Scrim
    scrim = androidx.compose.ui.graphics.Color(0xFF000000)
)

@Composable
fun SignOutwardV2Theme(
    darkTheme: Boolean = false, // Force light theme for this app
    dynamicColor: Boolean = false, // Disable dynamic color to maintain brand consistency
    content: @Composable () -> Unit
) {
    val colorScheme = SignOutwardColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = White.toArgb()
            window.navigationBarColor = White.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
