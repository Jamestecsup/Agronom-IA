package com.agronomia.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.navigation.compose.rememberNavController
import com.agronomia.domain.model.SpeciesCandidate
import com.agronomia.ui.capture.CaptureScreen
import com.agronomia.ui.capture.CaptureViewModel
import com.agronomia.ui.capture.IdentificationUiState
import com.agronomia.ui.chat.ChatScreen
import com.agronomia.ui.chat.ChatViewModel
import com.agronomia.ui.result.ResultScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Capture : Screen("capture", "Escáner", Icons.Filled.Search)
    data object Result : Screen("result", "Resultado", Icons.Filled.Home)
    data object Chat : Screen("chat", "Chat", Icons.Filled.Send)
    data object Garden : Screen("garden", "Mi Jardín", Icons.Filled.Home)
    data object Calendar : Screen("calendar", "Calendario", Icons.Filled.DateRange)
    data object Settings : Screen("settings", "Ajustes", Icons.Filled.Settings)
}

private val bottomItems = listOf(
    Screen.Capture,
    Screen.Chat,
    Screen.Garden,
    Screen.Calendar,
    Screen.Settings
)

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    // ViewModel compartido a nivel de Activity: Captura inicia la identificación
    // y Resultado/Chat observan el mismo estado sin volver a llamar a la red.
    val viewModel: CaptureViewModel = viewModel()
    val chatViewModel: ChatViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = Screen.Capture.route,
        modifier = modifier
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
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(Screen.Chat.route) {
            ChatScreen(viewModel = chatViewModel)
        }
        composable(Screen.Garden.route) {
            PlaceholderScreen("Mi Jardín")
        }
        composable(Screen.Calendar.route) {
            PlaceholderScreen("Calendario")
        }
        composable(Screen.Settings.route) {
            PlaceholderScreen("Ajustes")
        }
    }

    // Barra inferior con las pestañas de la app.
    NavigationBar(modifier = modifier) {
        bottomItems.forEach { screen ->
            NavigationBarItem(
                selected = false,
                onClick = {
                    when (screen) {
                        Screen.Result -> navController.navigate(Screen.Result.route) {
                            launchSingleTop = true
                        }
                        Screen.Chat -> {
                            // El chat toma la última planta detectada como contexto.
                            chatViewModel.setLastDetected(lastDetected(viewModel))
                            navController.navigate(Screen.Chat.route) {
                                launchSingleTop = true
                            }
                        }
                        else -> navController.navigate(screen.route) {
                            launchSingleTop = true
                        }
                    }
                },
                icon = { Icon(screen.icon, contentDescription = screen.label) },
                label = { Text(screen.label) }
            )
        }
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
