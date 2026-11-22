package com.rumput.vision.feature.cameras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.rumput.vision.R
import com.rumput.vision.core.design.component.CameraStatusIndicator
import com.rumput.vision.core.design.component.relativeTime
import com.rumput.vision.core.design.theme.RumputTheme
import com.rumput.vision.domain.model.CameraStatus
import com.rumput.vision.domain.model.CameraWithStatus

@Composable
fun CameraRow(item: CameraWithStatus, modifier: Modifier = Modifier) {
    val spacing = RumputTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.gutter, vertical = spacing.md)
            .testTag("camera_${item.camera.name}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(item.camera.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                detailLine(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CameraStatusIndicator(item.status)
    }
}

@Composable
private fun detailLine(item: CameraWithStatus): String {
    val stats = item.stats
    return when {
        item.status == CameraStatus.ONLINE && stats != null -> buildString {
            append(stringResource(R.string.camera_fps, stats.cameraFps))
            stats.connectionQuality?.let { append(" · ").append(stringResource(R.string.camera_connection_quality, it)) }
        }
        item.status == CameraStatus.DISABLED -> stringResource(R.string.camera_disabled_in_config)
        item.lastSeenOnlineEpochMillis != null ->
            stringResource(R.string.camera_last_seen, relativeTime(item.lastSeenOnlineEpochMillis))
        else -> item.camera.name
    }
}
