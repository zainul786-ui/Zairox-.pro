package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val JarvisDarkColorScheme = darkColorScheme(
    primary = CyanNeon,
    onPrimary = SpaceBlack,
    primaryContainer = SurfaceVariantDark,
    onPrimaryContainer = CyanNeon,
    secondary = ElectricBlue,
    onSecondary = SpaceBlack,
    secondaryContainer = SurfaceDark,
    onSecondaryContainer = TextWhite,
    tertiary = ListeningCyan,
    background = SpaceBlack,
    onBackground = TextWhite,
    surface = DeepMidnight,
    onSurface = TextWhite,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSilver,
    error = ErrorRed,
    onError = TextWhite
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JarvisDarkColorScheme,
        typography = Typography,
        content = content
    )
}
