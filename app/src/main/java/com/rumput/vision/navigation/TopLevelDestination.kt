package com.rumput.vision.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import com.rumput.vision.R
import kotlin.reflect.KClass

enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @param:StringRes val label: Int,
) {
    HOME(HomeRoute, HomeRoute::class, Icons.Filled.Home, Icons.Outlined.Home, R.string.nav_home),
    CAMERAS(CamerasRoute, CamerasRoute::class, Icons.Filled.Videocam, Icons.Outlined.Videocam, R.string.nav_cameras),
    EVENTS(EventsRoute, EventsRoute::class, Icons.Filled.NotificationsActive, Icons.Outlined.NotificationsActive, R.string.nav_events),
    RECORDINGS(RecordingsRoute, RecordingsRoute::class, Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary, R.string.nav_recordings),
    SETTINGS(SettingsRoute, SettingsRoute::class, Icons.Filled.Settings, Icons.Outlined.Settings, R.string.nav_settings),
}
