package com.agronomia.ui.theme

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Modos de tamaño de fuente disponibles en la aplicación.
 * Escalan la tipografía manteniendo intacta la estructura visual (dp).
 */
enum class FontScaleMode(
    val key: String,
    val label: String,
    val scaleFactor: Float,
    val description: String
) {
    SMALL(
        key = "small",
        label = "Pequeño",
        scaleFactor = 0.85f,
        description = "Texto compacto para visualizar más información en pantalla."
    ),
    MEDIUM(
        key = "medium",
        label = "Mediano",
        scaleFactor = 1.0f,
        description = "Tamaño de fuente estándar equilibrado (predeterminado)."
    ),
    LARGE(
        key = "large",
        label = "Grande",
        scaleFactor = 1.22f,
        description = "Texto ampliado para mayor comodidad y facilidad de lectura."
    );

    companion object {
        fun fromKey(key: String?): FontScaleMode =
            entries.find { it.key == key } ?: MEDIUM
    }
}

/**
 * Estado global de accesibilidad de la aplicación.
 */
data class AccessibilityState(
    val fontScaleMode: FontScaleMode = FontScaleMode.MEDIUM,
    val highContrast: Boolean = false
)

/**
 * CompositionLocal para consultar el estado de accesibilidad desde cualquier Composable.
 */
val LocalAccessibilityState = compositionLocalOf { AccessibilityState() }

/**
 * Gestor persistente de las preferencias de accesibilidad (Tamaño de fuente y Alto Contraste).
 */
object AccessibilityPreferences {
    private const val PREFS_NAME = "agronomia_accessibility_prefs"
    private const val KEY_FONT_SCALE = "font_scale_mode"
    private const val KEY_HIGH_CONTRAST = "high_contrast_enabled"

    private val _state = MutableStateFlow(AccessibilityState())
    val state: StateFlow<AccessibilityState> = _state.asStateFlow()

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedFont = FontScaleMode.fromKey(prefs.getString(KEY_FONT_SCALE, FontScaleMode.MEDIUM.key))
        val savedContrast = prefs.getBoolean(KEY_HIGH_CONTRAST, false)
        _state.value = AccessibilityState(
            fontScaleMode = savedFont,
            highContrast = savedContrast
        )
        initialized = true
    }

    fun setFontScaleMode(context: Context, mode: FontScaleMode) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FONT_SCALE, mode.key).apply()
        _state.value = _state.value.copy(fontScaleMode = mode)
    }

    fun setHighContrast(context: Context, enabled: Boolean) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_HIGH_CONTRAST, enabled).apply()
        _state.value = _state.value.copy(highContrast = enabled)
    }

    fun resetDefaults(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_FONT_SCALE, FontScaleMode.MEDIUM.key)
            .putBoolean(KEY_HIGH_CONTRAST, false)
            .apply()
        _state.value = AccessibilityState(
            fontScaleMode = FontScaleMode.MEDIUM,
            highContrast = false
        )
    }
}
