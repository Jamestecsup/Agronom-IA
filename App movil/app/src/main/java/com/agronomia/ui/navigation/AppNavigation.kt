package com.agronomia.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.agronomia.data.remote.NetworkModule
import com.agronomia.ui.calendar.CalendarScreen
import com.agronomia.ui.settings.SettingsScreen
import com.agronomia.ui.auth.UserLoginScreen
import com.agronomia.ui.capture.CaptureScreen
import com.agronomia.ui.capture.CaptureViewModel
import com.agronomia.ui.chat.ChatScreen
import com.agronomia.ui.chat.ChatViewModel
import com.agronomia.ui.components.AgronomIaBottomBar
import com.agronomia.ui.garden.GardenScreen
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
    var authenticated by remember {
        mutableStateOf(NetworkModule.currentUserToken() != null)
    }
    if (!authenticated) {
        UserLoginScreen(onAuthenticated = { authenticated = true })
        return
    }

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

    // MainActivity usa edge-to-edge: reserva el área de la barra de estado para
    // que encabezados como el del Chat no queden debajo de la hora/notificaciones.
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding()
    ) {
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
            composable(Screen.Garden.route) {
                GardenScreen(
                    viewModel = chatViewModel,
                    onOpenChat = { plantId ->
                        chatViewModel.selectPlant(plantId)
                        navController.navigate(Screen.Chat.route) { launchSingleTop = true }
                    }
                )
            }
            composable(Screen.Calendar.route) { CalendarScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
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
                        chatViewModel.refreshPlants()
                        navController.navigate(Screen.Chat.route) {
                            launchSingleTop = true
                        }
                    }
                    2 -> {
                        chatViewModel.refreshPlants()
                        navController.navigate(Screen.Garden.route) { launchSingleTop = true }
                    }
                    3 -> navController.navigate(Screen.Calendar.route) { launchSingleTop = true }
                    4 -> navController.navigate(Screen.Settings.route) { launchSingleTop = true }
                }
            }
        )
    }
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
