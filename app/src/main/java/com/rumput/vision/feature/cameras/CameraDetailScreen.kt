package com.rumput.vision.feature.cameras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rumput.vision.R
import com.rumput.vision.core.design.component.CameraSnapshot
import com.rumput.vision.core.design.component.CameraStatusIndicator
import com.rumput.vision.core.design.component.ConnectionIndicator
import com.rumput.vision.core.design.component.EmptyState
import com.rumput.vision.core.design.component.LoadingState
import com.rumput.vision.core.design.component.RumputScreen
import com.rumput.vision.core.design.component.SectionHeader
import com.rumput.vision.core.design.component.WarningText
import com.rumput.vision.core.design.component.previewAspectRatio
import com.rumput.vision.core.design.component.relativeTime
import com.rumput.vision.core.design.theme.RumputTheme
import com.rumput.vision.core.design.theme.tabular
import com.rumput.vision.data.mapper.cameraDisplayName
import com.rumput.vision.domain.model.CameraStatus
import com.rumput.vision.domain.model.CameraWithStatus
import com.rumput.vision.domain.model.ConnectionState

@Composable
fun CameraDetailScreen(
    onBack: () -> Unit,
    onOpenLiveView: (String) -> Unit,
    viewModel: CameraDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CameraDetailContent(state, onBack = onBack, onOpenLiveView = onOpenLiveView)
}

@Composable
fun CameraDetailContent(state: CameraDetailUiState, onBack: () -> Unit, onOpenLiveView: (String) -> Unit) {
    val title = state.camera?.camera?.displayName ?: cameraDisplayName(state.cameraName)
    RumputScreen(
        title = title,
        isTopLevel = false,
        onBack = onBack,
        modifier = Modifier.testTag("screen_camera_detail"),
        actions = { ConnectionIndicator(state.connection, Modifier.padding(end = RumputTheme.spacing.sm)) },
    ) { padding ->
        val modifier = Modifier.padding(padding).fillMaxSize()
        val camera = state.camera
        when {
            camera != null -> DetailBody(camera, state, modifier, onOpenLiveView)
            state.isLoading -> LoadingState(modifier)
            else -> EmptyState(
                icon = Icons.Outlined.VideocamOff,
                title = stringResource(R.string.camera_not_found_title),
                message = stringResource(R.string.camera_not_found_body, state.cameraName),
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun DetailBody(item: CameraWithStatus, state: CameraDetailUiState, modifier: Modifier, onOpenLiveView: (String) -> Unit) {
    val spacing = RumputTheme.spacing
    val camera = item.camera
    val stats = item.stats
    Column(modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 840.dp).fillMaxWidth()) {
            CameraSnapshot(
                url = state.snapshotUrl.takeIf { item.status != CameraStatus.DISABLED },
                refreshKey = state.snapshotTick,
                contentDescription = stringResource(R.string.camera_snapshot_description, camera.displayName),
                modifier = Modifier
                    .padding(horizontal = spacing.gutter)
                    .fillMaxWidth()
                    .aspectRatio(item.previewAspectRatio())
                    .clip(MaterialTheme.shapes.medium)
                    .testTag("camera_snapshot"),
            )
            Column(
                Modifier.padding(horizontal = spacing.gutter, vertical = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                val connected = state.connection is ConnectionState.Connected
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    CameraStatusIndicator(item.status)
                    // Stills only refresh while connected.
                    if (connected) {
                        Text(
                            stringResource(R.string.camera_snapshot_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // One notice: "unreachable" already says the information is the last known state.
                when {
                    state.connection is ConnectionState.Unreachable -> WarningText(stringResource(R.string.camera_detail_offline))
                    item.isCached -> WarningText(stringResource(R.string.cameras_cached_notice))
                }
                Button(
                    onClick = { onOpenLiveView(camera.name) },
                    enabled = item.status != CameraStatus.DISABLED && connected,
                    modifier = Modifier.fillMaxWidth().testTag("open_live_view"),
                ) {
                    Text(stringResource(R.string.camera_live_view))
                }
            }

            SectionHeader(stringResource(R.string.camera_section_health))
            InfoRow(
                stringResource(R.string.camera_info_fps),
                stats?.let { s -> s.expectedFps?.let { "%.1f / %.0f".format(s.cameraFps, it) } ?: "%.1f".format(s.cameraFps) },
            )
            InfoRow(stringResource(R.string.camera_info_connection), stats?.connectionQuality)
            InfoRow(stringResource(R.string.camera_info_reconnects), stats?.reconnectsLastHour?.toString())
            InfoRow(stringResource(R.string.camera_info_stalls), stats?.stallsLastHour?.toString())
            InfoRow(
                stringResource(R.string.camera_info_last_seen),
                item.lastSeenOnlineEpochMillis?.let { relativeTime(it) },
            )

            SectionHeader(stringResource(R.string.camera_section_config))
            InfoRow(
                stringResource(R.string.camera_info_resolution),
                if (camera.detectWidth != null && camera.detectHeight != null) "${camera.detectWidth} × ${camera.detectHeight}" else null,
            )
            InfoRow(stringResource(R.string.camera_info_detection), onOff(camera.detectEnabled))
            InfoRow(stringResource(R.string.camera_info_recording), onOff(camera.recordingEnabled))
            InfoRow(stringResource(R.string.camera_info_snapshots), onOff(camera.snapshotsEnabled))
            InfoRow(stringResource(R.string.camera_info_audio), onOff(camera.audioEnabled))
            InfoRow(stringResource(R.string.camera_info_name), camera.name)

            SectionHeader(stringResource(R.string.camera_section_streams))
            camera.mainStreamName?.let { InfoRow(stringResource(R.string.camera_info_main_stream), it) }
            if (camera.liveStreams.isEmpty()) {
                InfoRow(stringResource(R.string.camera_info_streams), null)
            } else {
                // Frigate labels often equal the go2rtc name; avoid showing the same text twice.
                val genericLabel = stringResource(R.string.camera_info_stream)
                camera.liveStreams.forEach { InfoRow(if (it.label == it.streamName) genericLabel else it.label, it.streamName) }
            }
        }
    }
}

@Composable
private fun onOff(enabled: Boolean) = stringResource(if (enabled) R.string.setting_on else R.string.setting_off)

@Composable
private fun InfoRow(label: String, value: String?) {
    val spacing = RumputTheme.spacing
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.gutter, vertical = spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value ?: "—", style = MaterialTheme.typography.bodyMedium.tabular)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
