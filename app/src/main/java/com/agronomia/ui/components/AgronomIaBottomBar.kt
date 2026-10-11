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
            path(fill = SolidColor(Color(0xFF1B5E20))) {
                moveTo(12f, 3f)
                curveTo(8f, 3f, 4f, 6f, 4f, 10f)
                curveTo(4f, 13f, 6.5f, 15.5f, 9.5f, 16.5f)
                lineTo(9.5f, 21f)
                lineTo(14.5f, 21f)
                lineTo(14.5f, 16.5f)
                curveTo(17.5f, 15.5f, 20f, 13f, 20f, 10f)
                curveTo(20f, 6f, 16f, 3f, 12f, 3f)
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
            path(fill = SolidColor(Color.Unspecified)) {
                moveTo(12f, 5f)
                curveTo(14.8f, 5f, 18f, 7.1f, 18f, 10f)
                curveTo(18f, 12.1f, 16.2f, 14f, 13.8f, 14.7f)
                lineTo(13.5f, 14.8f)
                lineTo(13.5f, 19f)
                lineTo(10.5f, 19f)
                lineTo(10.5f, 14.8f)
                lineTo(10.2f, 14.7f)
                curveTo(7.8f, 14f, 6f, 12.1f, 6f, 10f)
                curveTo(6f, 7.1f, 9.2f, 5f, 12f, 5f)
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

