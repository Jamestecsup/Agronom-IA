package com.agronomia.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.agronomia.domain.model.SpeciesCandidate
import com.agronomia.ui.capture.CaptureScreen
import com.agronomia.ui.capture.CaptureViewModel
import com.agronomia.ui.capture.IdentificationUiState
import com.agronomia.ui.chat.ChatScreen
import com.agronomia.ui.chat.ChatViewModel
import com.agronomia.ui.components.AgronomIaBottomBar
import com.agronomia.ui.result.ResultScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Capture : Screen("capture", "Escáner", Icons.Filled.Search)
    data object Result : Screen("result", "Resultado", Icons.Filled.Home)
    data object Chat : Screen("chat", "Chat", Icons.Filled.Send)
    data object Garden : Screen("garden", "Mi Jardín", Icons.Filled.Home)
    data object Calendar : Screen("calendar", "Calendario", Icons.Filled.DateRange)
    data object Settings : Screen("settings", "Ajustes", Icons.Filled.Settings)
}

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    // ViewModel compartido a nivel de Activity: Captura inicia la identificación
    // y Resultado/Chat observan el mismo estado sin volver a llamar a la red.
    val viewModel: CaptureViewModel = viewModel()
    val chatViewModel: ChatViewModel = viewModel()

    // Ruta actual para resaltar la pestaña activa en la píldora.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val selectedIndex = when (currentRoute) {
        Screen.Capture.route -> 0
        Screen.Chat.route -> 1
        Screen.Garden.route -> 2
        Screen.Calendar.route -> 3
        Screen.Settings.route -> 4
        else -> 0
    }

    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Capture.route,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            composable(Screen.Capture.route) {
                CaptureScreen(
                    viewModel = viewModel,
                    onNavigateToResult = {
                        navController.navigate(Screen.Result.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.Result.route) {
                ResultScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Chat.route) {
                ChatScreen(viewModel = chatViewModel)
            }
            composable(Screen.Garden.route) { PlaceholderScreen("Mi Jardín") }
            composable(Screen.Calendar.route) { PlaceholderScreen("Calendario") }
            composable(Screen.Settings.route) { PlaceholderScreen("Ajustes") }
        }

        // Único footer: la píldora flotante estilo Material 3 Expressive.
        AgronomIaBottomBar(
            modifier = Modifier.fillMaxWidth(),
            selectedIndex = selectedIndex,
            onItemSelected = { index ->
                when (index) {
                    0 -> navController.navigate(Screen.Capture.route) {
                        popUpTo(Screen.Capture.route) { inclusive = true }
                        launchSingleTop = true
                    }
                    1 -> {
                        // El chat toma la última planta detectada como contexto.
                        chatViewModel.setLastDetected(lastDetected(viewModel))
                        navController.navigate(Screen.Chat.route) {
                            launchSingleTop = true
                        }
                    }
                    2 -> navController.navigate(Screen.Garden.route) { launchSingleTop = true }
                    3 -> navController.navigate(Screen.Calendar.route) { launchSingleTop = true }
                    4 -> navController.navigate(Screen.Settings.route) { launchSingleTop = true }
                }
            }
        )
    }
}

/** Extrae la última planta detectada del estado compartido. */
private fun lastDetected(viewModel: CaptureViewModel): SpeciesCandidate? {
    val plant = (viewModel.uiState.value as? IdentificationUiState.Success)?.plant ?: return null
    return SpeciesCandidate(
        scientificName = plant.scientificName,
        commonNames = listOfNotNull(plant.commonName.ifBlank { null }),
        family = plant.family,
        confidence = plant.confidence
    )
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}
