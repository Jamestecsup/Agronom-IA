package com.agronomia.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val DarkColorScheme = darkColorScheme(
    primary = Green80,
    secondary = GreenGrey80,
    tertiary = LightGreen80
)

private val LightColorScheme = lightColorScheme(
    primary = Green40,
    secondary = GreenGrey40,
    tertiary = LightGreen40
)

/**
 * Esquema de colores de Alto Contraste inspirado en Canvas de Tecsup (WCAG AAA).
 * Transforma completamente la paleta de botones, textos, contenedores y bordes.
 */
private val HighContrastColorScheme = lightColorScheme(
    primary = HcPrimary,
    onPrimary = HcOnPrimary,
    primaryContainer = HcPrimaryContainer,
    onPrimaryContainer = HcOnPrimaryContainer,
    secondary = HcSecondary,
    onSecondary = HcOnSecondary,
    secondaryContainer = HcSecondaryContainer,
    onSecondaryContainer = HcOnSecondaryContainer,
    tertiary = HcTertiary,
    onTertiary = HcOnTertiary,
    tertiaryContainer = HcTertiaryContainer,
    onTertiaryContainer = HcOnTertiaryContainer,
    background = HcBackground,
    onBackground = HcOnBackground,
    surface = HcSurface,
    onSurface = HcOnSurface,
    surfaceVariant = HcSurfaceVariant,
    onSurfaceVariant = HcOnSurfaceVariant,
    surfaceContainer = HcSurfaceContainer,
    surfaceContainerHigh = HcSurfaceVariant,
    surfaceContainerLow = HcSurface,
    outline = HcOutline,
    outlineVariant = HcOutlineVariant,
    error = HcError,
    onError = HcOnError
)

/**
 * Tema principal de Agronom-IA.
 *
 * Aplica reactivamente las preferencias de accesibilidad:
 * - Escalado de tamaño de fuente (Pequeño, Mediano, Grande) mediante [LocalDensity] sin alterar
 *   las dimensiones estructurales en dp.
 * - Modo de Alto Contraste (estilo Canvas Tecsup) cambiando toda la paleta de textos y botones.
 */
@Composable
fun AgronomIaTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        AccessibilityPreferences.init(context)
    }

    val accessibilityState by AccessibilityPreferences.state.collectAsState()
    val currentDensity = LocalDensity.current

    val customDensity = Density(
        density = currentDensity.density,
        fontScale = accessibilityState.fontScaleMode.scaleFactor
    )

    val colorScheme = if (accessibilityState.highContrast) {
        HighContrastColorScheme
    } else {
        LightColorScheme
    }

    val activeTypography = buildTypography(highContrast = accessibilityState.highContrast)

    CompositionLocalProvider(
        LocalAccessibilityState provides accessibilityState,
        LocalDensity provides customDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = activeTypography,
            content = content
        )
    }
}
