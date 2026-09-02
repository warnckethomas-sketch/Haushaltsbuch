package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = HighDensityPrimaryContainer,
    onPrimary = HighDensityOnPrimaryContainer,
    primaryContainer = HighDensityPrimary,
    onPrimaryContainer = HighDensityPrimaryContainer,
    secondary = HighDensitySecondaryContainer,
    onSecondary = HighDensityOnSecondaryContainer,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = HighDensityPrimary,
    onPrimary = HighDensityOnPrimary,
    primaryContainer = HighDensityPrimaryContainer,
    onPrimaryContainer = HighDensityOnPrimaryContainer,
    secondary = HighDensitySecondary,
    onSecondary = HighDensityOnSecondary,
    secondaryContainer = HighDensitySecondaryContainer,
    onSecondaryContainer = HighDensityOnSecondaryContainer,
    background = HighDensityBackground,
    surface = HighDensitySurface,
    surfaceVariant = HighDensitySurfaceVariant,
    onSurface = HighDensityOnSurface,
    onSurfaceVariant = HighDensityOnSurfaceVariant,
    outline = HighDensityOutline
)

@Composable
fun HaushaltsbuchTheme(
    themeKey: String = "PURPLE",
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val preset = AppThemePresets.getPresetByKey(themeKey)
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> preset.darkColorScheme
        else -> preset.lightColorScheme
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
