package com.rumput.vision.feature.recordings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rumput.vision.R
import com.rumput.vision.core.design.component.PendingFeatureScreen

@Composable
fun RecordingsScreen() {
    PendingFeatureScreen(
        title = stringResource(R.string.nav_recordings),
        icon = Icons.Outlined.VideoLibrary,
        message = stringResource(R.string.recordings_pending_body),
        testTag = "screen_recordings",
    )
}
