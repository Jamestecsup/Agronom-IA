package com.agronomia.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.agronomia.ui.theme.AgronomIaTheme
import com.agronomia.ui.theme.LocalAccessibilityState

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

/**
 * TopBar personalizada simplificada para Agronom-IA.
 *
 * Muestra el ícono/logo y la identificación de la aplicación e institución ("Agronom-IA" y "TECSUP").
 * Se adapta automáticamente al modo de alto contraste.
 */
@Composable
fun AgronomIaTopBar(
    modifier: Modifier = Modifier
) {
    val highContrast = LocalAccessibilityState.current.highContrast

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (highContrast) Color(0xFFFFFFFF) else MaterialTheme.colorScheme.surface,
        tonalElevation = if (highContrast) 0.dp else 3.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val circleBg = if (highContrast) {
                        Color(0xFF002855)
                    } else {
                        Color(0xFF2E7D32).copy(alpha = 0.15f)
                    }
                    val iconTint = if (highContrast) {
                        Color(0xFFFFEA00)
                    } else {
                        Color(0xFF1B5E20)
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(circleBg)
                            .then(
                                if (highContrast) {
                                    Modifier.border(2.dp, Color.Black, CircleShape)
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = EcoLeafIcon,
                            contentDescription = "Logo Agronom-IA",
                            tint = iconTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Agronom-IA",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (highContrast) FontWeight.ExtraBold else FontWeight.Bold,
                            color = if (highContrast) Color(0xFF000000) else Color(0xFF1B5E20)
                        )
                        Text(
                            text = "TECSUP",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (highContrast) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (highContrast) Color(0xFF002855) else Color(0xFF003366)
                        )
                    }
                }
            }
            if (highContrast) {
                HorizontalDivider(thickness = 2.dp, color = Color(0xFF000000))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AgronomIaTopBarPreview() {
    AgronomIaTheme {
        AgronomIaTopBar()
    }
}
