package com.agronomia.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agronomia.ui.components.AgronomIaTopBar
import com.agronomia.ui.theme.AccessibilityPreferences
import com.agronomia.ui.theme.FontScaleMode
import com.agronomia.ui.theme.LocalAccessibilityState

/**
 * Pantalla de Ajustes de Accesibilidad de Agronom-IA.
 *
 * Controles incluidos:
 * 1. Selector de modo de tamaño de fuente: Pequeño, Mediano o Grande sin alterar la estructura visual.
 * 2. Modo de Alto Contraste (estilo Canvas de Tecsup): transforma completamente la paleta de colores
 *    de todos los textos, contenedores, bordes y botones de la aplicación.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accessibilityState = LocalAccessibilityState.current
    val highContrast = accessibilityState.highContrast
    val currentFontMode = accessibilityState.fontScaleMode

    Scaffold(
        topBar = {
            AgronomIaTopBar()
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cabecera de la sección
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (highContrast) Color(0xFF002855)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        )
                        .then(
                            if (highContrast) Modifier.border(2.dp, Color.Black, CircleShape)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null,
                        tint = if (highContrast) Color(0xFFFFEA00) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Accesibilidad y Visualización",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Configura el tamaño de texto y el contraste visual de la interfaz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 1. Control de Tamaño de Fuente (Pequeño, Mediano, Grande)
            FontScaleCard(
                currentMode = currentFontMode,
                highContrast = highContrast,
                onSelectMode = { mode ->
                    AccessibilityPreferences.setFontScaleMode(context, mode)
                }
            )

            // 2. Control de Modo de Alto Contraste (Estilo Canvas Tecsup)
            HighContrastCard(
                enabled = highContrast,
                onToggle = { enabled ->
                    AccessibilityPreferences.setHighContrast(context, enabled)
                }
            )

            // Botón para restaurar valores por defecto
            OutlinedButton(
                onClick = { AccessibilityPreferences.resetDefaults(context) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    width = if (highContrast) 2.dp else 1.dp,
                    color = if (highContrast) Color(0xFF000000) else MaterialTheme.colorScheme.outline
                ),
                colors = if (highContrast) {
                    ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFFE4ECF7),
                        contentColor = Color(0xFF000000)
                    )
                } else {
                    ButtonDefaults.outlinedButtonColors()
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Restablecer ajustes predeterminados",
                    fontWeight = if (highContrast) FontWeight.ExtraBold else FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FontScaleCard(
    currentMode: FontScaleMode,
    highContrast: Boolean,
    onSelectMode: (FontScaleMode) -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (highContrast) Color(0xFFFFFFFF) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (highContrast) 2.dp else 1.dp,
            color = if (highContrast) Color(0xFF000000) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Tamaño de fuente",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (highContrast) Color(0xFF002855)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = currentMode.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (highContrast) Color(0xFFFFEA00) else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Ajusta la escala tipográfica en todas las pantallas manteniendo intacta la estructura visual.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Selector de 3 opciones: Pequeño | Mediano | Grande
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FontScaleMode.entries.forEach { mode ->
                    val selected = mode == currentMode
                    val containerBg = when {
                        selected && highContrast -> Color(0xFF002855)
                        selected && !highContrast -> MaterialTheme.colorScheme.primary
                        !selected && highContrast -> Color(0xFFE4ECF7)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    }
                    val contentColor = when {
                        selected && highContrast -> Color(0xFFFFFFFF)
                        selected && !highContrast -> MaterialTheme.colorScheme.onPrimary
                        !selected && highContrast -> Color(0xFF000000)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    val borderStroke = when {
                        highContrast -> BorderStroke(2.dp, Color(0xFF000000))
                        selected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    }

                    OutlinedCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(role = Role.RadioButton) { onSelectMode(mode) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = containerBg),
                        border = borderStroke
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Muestra visual de "Aa" con tamaño fijo relativo para que no desborde la tarjeta
                            val sampleSp = when (mode) {
                                FontScaleMode.SMALL -> 13.sp
                                FontScaleMode.MEDIUM -> 16.sp
                                FontScaleMode.LARGE -> 20.sp
                            }
                            Text(
                                text = "Aa",
                                fontSize = sampleSp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (selected && highContrast) Color(0xFFFFEA00) else contentColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected || highContrast) FontWeight.Bold else FontWeight.Medium,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = currentMode.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HighContrastCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onToggle(!enabled) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (enabled) Color(0xFFE4ECF7) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (enabled) 2.dp else 1.dp,
            color = if (enabled) Color(0xFF000000) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Modo de alto contraste",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Cambia completamente la paleta de colores de textos, bordes y botones (estilo Canvas Tecsup).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    thumbContent = if (enabled) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                                tint = Color(0xFF002855)
                            )
                        }
                    } else null,
                    colors = if (enabled) {
                        SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFFFEA00),
                            checkedTrackColor = Color(0xFF002855),
                            checkedBorderColor = Color(0xFF000000)
                        )
                    } else {
                        SwitchDefaults.colors()
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(
                thickness = if (enabled) 1.5.dp else 1.dp,
                color = if (enabled) Color(0xFF000000) else MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (enabled) Color(0xFF002855) else Color(0xFF2E7D32))
                        .then(
                            if (enabled) Modifier.border(1.5.dp, Color.Black, CircleShape)
                            else Modifier
                        )
                )
                Text(
                    text = if (enabled) {
                        "Paleta activa: Alto Contraste Canvas Tecsup (Azul Marino / Negro / Amarillo)"
                    } else {
                        "Paleta activa: Estándar Botánica Agronom-IA (Verde Natural)"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

