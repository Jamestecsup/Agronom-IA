package com.agronomia.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Ícono personalizado de Hoja/Eco */
private val EcoLeafIcon: ImageVector
    get() {
        if (_ecoLeafIcon != null) return _ecoLeafIcon!!
        _ecoLeafIcon = ImageVector.Builder(
            name = "EcoLeaf",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF1B5E20))) {
                moveTo(16.05f, 3f)
                curveTo(12f, 3f, 7f, 5.5f, 5f, 10f)
                curveTo(3f, 14.5f, 4f, 19.5f, 7.5f, 22.5f)
                curveTo(9.5f, 24f, 13f, 23f, 15.5f, 21f)
                curveTo(18.5f, 18.5f, 20f, 14.5f, 20f, 10f)
                curveTo(20f, 5.5f, 18f, 3f, 16.05f, 3f)
                close()
                moveTo(6.8f, 11.8f)
                curveTo(8.3f, 8.2f, 12.1f, 6.1f, 15.5f, 5.2f)
                curveTo(15.3f, 8.7f, 13.4f, 12.4f, 10f, 14f)
                curveTo(8.8f, 14.6f, 7.6f, 14.9f, 6.8f, 11.8f)
                close()
            }
        }.build()
        return _ecoLeafIcon!!
    }
private var _ecoLeafIcon: ImageVector? = null

/** Ícono personalizado de Contraste (mitad claro / mitad oscuro) */
private val ContrastHalfIcon: ImageVector
    get() {
        if (_contrastHalfIcon != null) return _contrastHalfIcon!!
        _contrastHalfIcon = ImageVector.Builder(
            name = "ContrastHalf",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Unspecified)) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
                curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
                close()
                moveTo(12f, 20f)
                lineTo(12f, 4f)
                curveTo(16.41f, 4f, 20f, 7.59f, 20f, 12f)
                curveTo(20f, 16.41f, 16.41f, 20f, 12f, 20f)
                close()
            }
        }.build()
        return _contrastHalfIcon!!
    }
private var _contrastHalfIcon: ImageVector? = null

/**
 * TopBar personalizada para Agronom-IA según especificaciones Material Design 3.
 *
 * Incluye:
 * - Alineación Izquierda: Logo + Column ("Agronom-IA" en verde oscuro y "TECSUP" en azul institucional).
 * - Alineación Derecha: Controles de accesibilidad (Botón Contraste, Botón Tamaño de Fuente "TT")
 *   y Avatar circular de usuario (HU-4).
 */
@Composable
fun AgronomIaTopBar(
    modifier: Modifier = Modifier,
    onContrastClick: () -> Unit = {},
    onFontSizeClick: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Alineación Izquierda: Logo Placeholder + Agronom-IA / TECSUP
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Logo de Hoja / Planta con fondo verde suave
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2E7D32).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = EcoLeafIcon,
                        contentDescription = "Logo Agronom-IA",
                        tint = Color(0xFF1B5E20),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = "Agronom-IA",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B5E20) // Verde oscuro
                    )
                    Text(
                        text = "TECSUP",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF003366) // Azul institucional TECSUP
                    )
                }
            }

            // Alineación Derecha: Controles de accesibilidad (Contraste, "TT") + Avatar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Control de Accesibilidad 1: Contraste
                IconButton(
                    onClick = onContrastClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        imageVector = ContrastHalfIcon,
                        contentDescription = "Ajustar Contraste",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Control de Accesibilidad 2: Tamaño de Fuente "TT"
                IconButton(
                    onClick = onFontSizeClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = "TT",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Avatar Circular
                IconButton(
                    onClick = onProfileClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE8F5E9))
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Perfil del usuario",
                        tint = Color(0xFF1B5E20),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
