package com.djassistant.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.djassistant.ui.debug.DebugScreen
import com.djassistant.ui.main.MainScreen
import com.djassistant.ui.settings.SettingsScreen

// iOS-style push/pop: pure slide, no fade/scale/dimming — avoids the
// "flash" that Compose Navigation's default crossfade produces.
private const val NAV_ANIM_DURATION_MS = 300
private val NavEasing = tween<IntOffset>(
    durationMillis = NAV_ANIM_DURATION_MS,
    easing = FastOutSlowInEasing
)

@Composable
fun DjNavGraph(
    appVersion: String,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, NavEasing)
        },
        exitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, NavEasing)
        },
        popEnterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, NavEasing)
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, NavEasing)
        }
    ) {
        composable(Screen.Main.route) {
            MainScreen(
                appVersion = appVersion,
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToDebug = { navController.navigate(Screen.Debug.route) }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Debug.route) {
            DebugScreen(
                appVersion = appVersion,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
