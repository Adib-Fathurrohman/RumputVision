package com.rumput.vision.feature.events

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rumput.vision.R
import com.rumput.vision.core.design.component.PendingFeatureScreen

@Composable
fun EventsScreen() {
    PendingFeatureScreen(
        title = stringResource(R.string.nav_events),
        icon = Icons.Outlined.NotificationsActive,
        message = stringResource(R.string.events_pending_body),
        testTag = "screen_events",
    )
}
