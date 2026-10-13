package com.agronomia.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
 * Tema principal de Agronom-IA.
 *
 * Se ha bloqueado la aplicación para forzar siempre el esquema de colores claros (LightColorScheme)
 * e ignorar la configuración de modo oscuro del sistema (isSystemInDarkTheme), garantizando que el
 * fondo permanezca claro y se respeten los colores de la marca en todo momento.
 */
@Composable
fun AgronomIaTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Forzado a utilizar siempre LightColorScheme
    val colorScheme = LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                // Fuerza íconos oscuros en la barra de estado e inferior para que sean
                // legibles sobre fondos claros, aun con el modo oscuro activado en el celular.
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = true
                    isAppearanceLightNavigationBars = true
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
