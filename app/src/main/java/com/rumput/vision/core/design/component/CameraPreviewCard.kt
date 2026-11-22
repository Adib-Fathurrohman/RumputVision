package com.rumput.vision.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.rumput.vision.R
import com.rumput.vision.core.design.theme.RumputTheme
import com.rumput.vision.domain.model.CameraStatus
import com.rumput.vision.domain.model.CameraWithStatus

/** Aspect ratio of the camera's detect resolution, or 16:9. Clamped so odd configs cannot break layouts. */
fun CameraWithStatus.previewAspectRatio(): Float {
    val w = camera.detectWidth
    val h = camera.detectHeight
    return if (w != null && h != null && w > 0 && h > 0) (w.toFloat() / h).coerceIn(0.5f, 3f) else 16f / 9f
}

/**
 * Grid tile: latest frame, camera name and status. Frames are dimmed and labelled when the status is not
 * live, so an old frame is never mistaken for the current view.
 */
@Composable
fun CameraPreviewCard(
    item: CameraWithStatus,
    snapshotUrl: String?,
    refreshKey: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = RumputTheme.spacing
    val live = item.status == CameraStatus.ONLINE
    val statusColor = when (item.status) {
        CameraStatus.ONLINE -> RumputTheme.statusColors.online
        CameraStatus.OFFLINE -> RumputTheme.statusColors.offline
        CameraStatus.DISABLED, CameraStatus.UNKNOWN -> RumputTheme.statusColors.unknown
    }
    val statusLabel = stringResource(
        when (item.status) {
            CameraStatus.ONLINE -> R.string.camera_status_online
            CameraStatus.OFFLINE -> R.string.camera_status_offline
            CameraStatus.DISABLED -> R.string.camera_status_disabled
            CameraStatus.UNKNOWN -> R.string.camera_status_unknown
        },
    )
    val description = stringResource(R.string.camera_preview_description, item.camera.displayName, statusLabel)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(item.previewAspectRatio())
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClickLabel = stringResource(R.string.camera_open_details), role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag("preview_${item.camera.name}"),
    ) {
        CameraSnapshot(
            url = snapshotUrl.takeIf { item.status != CameraStatus.DISABLED },
            refreshKey = refreshKey,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        if (!live) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = spacing.sm, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            StatusDot(statusColor)
            Text(
                item.camera.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!live) {
                Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}
