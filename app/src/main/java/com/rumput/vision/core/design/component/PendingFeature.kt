package com.rumput.vision.core.design.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag

/** A top-level screen whose feature is not implemented in this build. States that plainly. */
@Composable
fun PendingFeatureScreen(title: String, icon: ImageVector, message: String, testTag: String) {
    RumputScreen(title = title, modifier = Modifier.testTag(testTag)) { padding ->
        EmptyState(
            icon = icon,
            title = title,
            message = message,
            modifier = Modifier.padding(padding).fillMaxSize(),
        )
    }
}
