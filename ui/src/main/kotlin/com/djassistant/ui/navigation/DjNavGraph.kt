package com.djassistant.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.djassistant.ui.debug.DebugScreen
import com.djassistant.ui.main.MainScreen
import com.djassistant.ui.settings.SettingsScreen

@Composable
fun DjNavGraph(
    appVersion: String,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route
    ) {
        composable(Screen.Main.route) {
            MainScreen(
                appVersion = appVersion,
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Debug.route) {
            DebugScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
