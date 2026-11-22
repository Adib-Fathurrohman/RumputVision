package com.rumput.vision.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rumput.vision.R
import com.rumput.vision.core.design.theme.RumputTheme
import com.rumput.vision.domain.model.CameraStatus
import com.rumput.vision.domain.model.ConnectionRoute
import com.rumput.vision.domain.model.ConnectionState

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

/** Compact dot + label. The whole indicator reads as one item for screen readers. */
@Composable
fun StatusLabel(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusDot(color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun ConnectionIndicator(state: ConnectionState, modifier: Modifier = Modifier) {
    val colors = RumputTheme.statusColors
    val (color, label) = when (state) {
        is ConnectionState.Connected -> colors.online to stringResource(
            when (state.route) {
                ConnectionRoute.LOCAL -> R.string.connection_connected_local
                ConnectionRoute.REMOTE -> R.string.connection_connected_remote
            },
        )
        ConnectionState.Connecting -> colors.warning to stringResource(R.string.connection_connecting)
        ConnectionState.NotConfigured -> colors.unknown to stringResource(R.string.connection_not_configured)
        is ConnectionState.Unauthorized -> colors.offline to stringResource(R.string.connection_unauthorized)
        is ConnectionState.Unreachable -> colors.offline to stringResource(R.string.connection_unreachable)
        ConnectionState.Unknown -> colors.unknown to stringResource(R.string.connection_unknown)
    }
    StatusLabel(color = color, label = label, modifier = modifier)
}

@Composable
fun CameraStatusIndicator(status: CameraStatus, modifier: Modifier = Modifier) {
    val colors = RumputTheme.statusColors
    val (color, label) = when (status) {
        CameraStatus.ONLINE -> colors.online to stringResource(R.string.camera_status_online)
        CameraStatus.OFFLINE -> colors.offline to stringResource(R.string.camera_status_offline)
        CameraStatus.DISABLED -> colors.unknown to stringResource(R.string.camera_status_disabled)
        CameraStatus.UNKNOWN -> colors.unknown to stringResource(R.string.camera_status_unknown)
    }
    StatusLabel(color = color, label = label, modifier = modifier)
}
