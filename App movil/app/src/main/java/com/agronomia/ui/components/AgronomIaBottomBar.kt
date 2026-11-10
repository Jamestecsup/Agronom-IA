package com.agronomia.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agronomia.ui.theme.LocalAccessibilityState

/** Ícono de Jardín / Planta (Filled) */
private val GardenFilledIcon: ImageVector
    get() {
        if (_gardenFilledIcon != null) return _gardenFilledIcon!!
        _gardenFilledIcon = ImageVector.Builder(
            name = "GardenFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3f)
                curveTo(8f, 3f, 4f, 6.5f, 4f, 11f)
                curveTo(4f, 14.5f, 6.5f, 17f, 9.5f, 18f)
                lineTo(9.5f, 21f)
                lineTo(14.5f, 21f)
                lineTo(14.5f, 18f)
                curveTo(17.5f, 17f, 20f, 14.5f, 20f, 11f)
                curveTo(20f, 6.5f, 16f, 3f, 12f, 3f)
                close()
                moveTo(12f, 15f)
                curveTo(9.8f, 15f, 8f, 13.2f, 8f, 11f)
                curveTo(8f, 8.8f, 9.8f, 7f, 12f, 7f)
                curveTo(14.2f, 7f, 16f, 8.8f, 16f, 11f)
                curveTo(16f, 13.2f, 14.2f, 15f, 12f, 15f)
                close()
            }
        }.build()
        return _gardenFilledIcon!!
    }
private var _gardenFilledIcon: ImageVector? = null

/** Ícono de Jardín / Planta (Outlined) */
private val GardenOutlinedIcon: ImageVector
    get() {
        if (_gardenOutlinedIcon != null) return _gardenOutlinedIcon!!
        _gardenOutlinedIcon = ImageVector.Builder(
            name = "GardenOutlined",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3f)
                curveTo(8.5f, 3f, 5f, 6f, 5f, 10.5f)
                curveTo(5f, 13.8f, 7.3f, 16.5f, 10.5f, 17.5f)
                lineTo(10.5f, 21f)
                lineTo(13.5f, 21f)
                lineTo(13.5f, 17.5f)
                curveTo(16.7f, 16.5f, 19f, 13.8f, 19f, 10.5f)
                curveTo(19f, 6f, 15.5f, 3f, 12f, 3f)
                close()
                moveTo(12f, 15.5f)
                curveTo(9.2f, 15.5f, 7f, 13.3f, 7f, 10.5f)
                curveTo(7f, 7.7f, 9.2f, 5f, 12f, 5f)
                curveTo(14.8f, 5f, 17f, 7.7f, 17f, 10.5f)
                curveTo(17f, 13.3f, 14.8f, 15.5f, 12f, 15.5f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 7f)
                curveTo(10.1f, 7f, 8.5f, 8.6f, 8.5f, 10.5f)
                lineTo(10.5f, 10.5f)
                curveTo(10.5f, 9.7f, 11.2f, 9f, 12f, 9f)
                lineTo(12f, 7f)
                close()
            }
        }.build()
        return _gardenOutlinedIcon!!
    }
private var _gardenOutlinedIcon: ImageVector? = null

/** Elemento de navegación para la barra flotante */
data class BottomNavItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(
        title = "Escáner",
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search
    ),
    BottomNavItem(
        title = "Chat",
        selectedIcon = Icons.Filled.Send,
        unselectedIcon = Icons.Outlined.Send
    ),
    BottomNavItem(
        title = "Mi Jardín",
        selectedIcon = GardenFilledIcon,
        unselectedIcon = GardenOutlinedIcon
    ),
    BottomNavItem(
        title = "Calendario",
        selectedIcon = Icons.Filled.DateRange,
        unselectedIcon = Icons.Outlined.DateRange
    ),
    BottomNavItem(
        title = "Ajustes",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
)

/**
 * Barra de navegación inferior flotante personalizada estilo Material Design 3 Expressive.
 *
 * Se adapta al modo de Alto Contraste cambiando la píldora activa y los íconos a la paleta
 * de alto contraste de Canvas Tecsup, y mantiene 1 sola línea por etiqueta para no alterar
 * la estructura visual en ningún tamaño de fuente.
 */
@Composable
fun AgronomIaBottomBar(
    modifier: Modifier = Modifier,
    selectedIndex: Int = 0,
    onItemSelected: (Int) -> Unit = {}
) {
    val highContrast = LocalAccessibilityState.current.highContrast

    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = if (highContrast) 0.dp else 10.dp,
        border = if (highContrast) BorderStroke(2.dp, Color.Black) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            bottomNavItems.forEachIndexed { index, item ->
                val isSelected = selectedIndex == index
                val accent = if (highContrast) Color(0xFF002855) else MaterialTheme.colorScheme.primary
                Column(
                    modifier = Modifier.weight(1f).clickable { onItemSelected(index) }
                        .background(
                            color = if (isSelected) {
                                if (highContrast) Color(0xFFFFEA00) else MaterialTheme.colorScheme.primaryContainer
                            } else Color.Transparent,
                            shape = RoundedCornerShape(20.dp)
                        ).padding(horizontal = 2.dp, vertical = 5.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier.size(30.dp).background(
                            color = if (isSelected && !highContrast) MaterialTheme.colorScheme.surface.copy(alpha = 0.8f) else Color.Transparent,
                            shape = CircleShape
                        ),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = null,
                            tint = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected || highContrast) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun AgronomIaBottomBarPreview() {
    com.agronomia.ui.theme.AgronomIaTheme {
        AgronomIaBottomBar()
    }
}
