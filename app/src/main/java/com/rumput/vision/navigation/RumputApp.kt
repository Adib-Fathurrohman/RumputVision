package com.rumput.vision.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rumput.vision.core.design.component.LoadingState

@Composable
fun RumputApp(viewModel: AppViewModel = hiltViewModel()) {
    val startState by viewModel.startState.collectAsStateWithLifecycle()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val state = startState) {
            AppStartState.Loading -> LoadingState()
            is AppStartState.Ready -> {
                // Decided once: later profile changes must not reset the navigation graph.
                val start = remember { if (state.hasProfiles) HomeRoute else OnboardingRoute }
                RumputAppContent(startDestination = start)
            }
        }
    }
}

@Composable
fun RumputAppContent(startDestination: Any) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showNavigation = TopLevelDestination.entries.any { destination.isOn(it) }

    val adaptiveType = NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { item ->
                val selected = destination.isOn(item)
                item(
                    selected = selected,
                    onClick = { navController.navigateToTopLevel(item) },
                    icon = { Icon(if (selected) item.selectedIcon else item.unselectedIcon, contentDescription = null) },
                    label = { Text(stringResource(item.label)) },
                    modifier = Modifier.testTag("nav_${item.name.lowercase()}"),
                )
            }
        },
        layoutType = if (showNavigation) adaptiveType else NavigationSuiteType.None,
    ) {
        RumputNavHost(navController = navController, startDestination = startDestination)
    }
}

private fun NavDestination?.isOn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true
