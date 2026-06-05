package com.atomik.quickbeep.ui.theme

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

// Premium color palettes obeying the "Purple Ban"
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF3B82F6),        // Vibrant Blue
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1E3A8A), // Dark Navy
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF14B8A6),      // Teal
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF0F766E),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = Color(0xFFF59E0B),       // Amber
    onTertiary = Color(0xFF0F172A),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = Color(0xFF0B0F19),     // Midnight Dark (Slate-950 equivalent)
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF111827),        // Slate-900 Card surfaces
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1F2937),  // Slate-800
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF64748B)         // Slate-500
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1D4ED8),        // Navy Blue
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E40AF),
    secondary = Color(0xFF0D9488),      // Dark Teal
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF115E59),
    tertiary = Color(0xFFD97706),       // Warm Amber
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF92400E),
    background = Color(0xFFF8FAFC),     // Soft light background
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),  // Slate-100
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFF94A3B8)
)

@Composable
fun AtomikQuickBeepTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
