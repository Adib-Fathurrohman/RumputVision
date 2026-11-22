package com.rumput.vision.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.rumput.vision.feature.cameras.CameraDetailScreen
import com.rumput.vision.feature.cameras.CamerasScreen
import com.rumput.vision.feature.dashboard.DashboardScreen
import com.rumput.vision.feature.events.EventsScreen
import com.rumput.vision.feature.onboarding.OnboardingScreen
import com.rumput.vision.feature.recordings.RecordingsScreen
import com.rumput.vision.feature.settings.AboutScreen
import com.rumput.vision.feature.settings.ServerProfileEditScreen
import com.rumput.vision.feature.settings.SettingsScreen

@Composable
fun RumputNavHost(
    navController: NavHostController,
    startDestination: Any,
    modifier: Modifier = Modifier,
) {
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                onAddServer = { navController.navigate(ServerProfileEditRoute(fromOnboarding = true)) },
            )
        }
        composable<HomeRoute> {
            DashboardScreen(
                onAddServer = { navController.navigate(ServerProfileEditRoute()) },
                onEditServer = { id -> navController.navigate(ServerProfileEditRoute(profileId = id)) },
                onOpenCamera = { name -> navController.navigate(CameraDetailRoute(name)) },
            )
        }
        composable<CamerasRoute> {
            CamerasScreen(onOpenCamera = { name -> navController.navigate(CameraDetailRoute(name)) })
        }
        composable<CameraDetailRoute> { CameraDetailScreen(onBack = navController::navigateUp) }
        composable<EventsRoute> { EventsScreen() }
        composable<RecordingsRoute> { RecordingsScreen() }
        composable<SettingsRoute> {
            SettingsScreen(
                onAddServer = { navController.navigate(ServerProfileEditRoute()) },
                onEditServer = { id -> navController.navigate(ServerProfileEditRoute(profileId = id)) },
                onOpenAbout = { navController.navigate(AboutRoute) },
            )
        }
        composable<AboutRoute> { AboutScreen(onBack = navController::navigateUp) }
        composable<ServerProfileEditRoute> { entry ->
            val route = entry.toRoute<ServerProfileEditRoute>()
            ServerProfileEditScreen(
                onBack = navController::navigateUp,
                onFinished = {
                    if (route.fromOnboarding) {
                        navController.navigate(HomeRoute) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    } else {
                        navController.navigateUp()
                    }
                },
            )
        }
    }
}

fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
