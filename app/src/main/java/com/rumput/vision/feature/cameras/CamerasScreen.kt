package com.rumput.vision.feature.cameras

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rumput.vision.R
import com.rumput.vision.core.design.component.PendingFeatureScreen

@Composable
fun CamerasScreen() {
    PendingFeatureScreen(
        title = stringResource(R.string.nav_cameras),
        icon = Icons.Outlined.Videocam,
        message = stringResource(R.string.cameras_pending_body),
        testTag = "screen_cameras",
    )
}
