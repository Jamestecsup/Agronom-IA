package com.agronomia.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.agronomia.ui.capture.CaptureScreen
import com.agronomia.ui.capture.CaptureViewModel
import com.agronomia.ui.result.ResultScreen

sealed class Screen(val route: String) {
    data object Capture : Screen("capture")
    data object Result : Screen("result")
}

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    // ViewModel compartido a nivel de Activity: Captura inicia la identificación
    // y Resultado observa el mismo estado sin volver a llamar a la red.
    val viewModel: CaptureViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = Screen.Capture.route,
        modifier = modifier
    ) {
        composable(Screen.Capture.route) {
            CaptureScreen(
                viewModel = viewModel,
                onNavigateToResult = {
                    // launchSingleTop evita duplicar la pantalla si se pulsa dos veces.
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
    }
}
