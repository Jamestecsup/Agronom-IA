package com.agronomia.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.agronomia.ui.capture.CaptureScreen
import com.agronomia.ui.result.ResultScreen

// TODO: Extender las rutas de navegación y pasar argumentos (e.g. ID o URI de la imagen identificada)

sealed class Screen(val route: String) {
    data object Capture : Screen("capture")
    data object Result : Screen("result")
}

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Capture.route,
        modifier = modifier
    ) {
        composable(Screen.Capture.route) {
            CaptureScreen(
                onNavigateToResult = {
                    navController.navigate(Screen.Result.route)
                }
            )
        }
        composable(Screen.Result.route) {
            ResultScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
