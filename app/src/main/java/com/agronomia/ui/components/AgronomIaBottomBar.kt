package com.agronomia.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

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
 * Características:
 * - Flotante: separada de los bordes con padding exterior (16.dp).
 * - Bordes muy redondeados (`RoundedCornerShape(100.dp)` / Píldora) y elevación con sombra.
 * - Indicador activo estilo píldora verde claro con texto e ícono verde oscuro (`#1B5E20`).
 */
@Composable
fun AgronomIaBottomBar(
    modifier: Modifier = Modifier,
    selectedIndex: Int = 0,
    onItemSelected: (Int) -> Unit = {}
) {
    Card(
        modifier = modifier
            .navigationBarsPadding()
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        shape = RoundedCornerShape(100.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            windowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier.fillMaxWidth()
        ) {
            bottomNavItems.forEachIndexed { index, item ->
                val isSelected = selectedIndex == index
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onItemSelected(index) },
                    icon = {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.title
                        )
                    },
                    label = {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color(0xFFC8E6C9), // Verde claro para la píldora activa
                        selectedIconColor = Color(0xFF1B5E20), // Verde oscuro para el ícono activo
                        selectedTextColor = Color(0xFF1B5E20), // Verde oscuro para el texto activo
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
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

