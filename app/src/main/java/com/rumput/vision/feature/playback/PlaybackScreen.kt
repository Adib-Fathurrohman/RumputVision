package com.rumput.vision.feature.playback

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import com.rumput.vision.R
import com.rumput.vision.core.design.component.RumputScreen
import com.rumput.vision.core.design.component.dayLabel
import com.rumput.vision.core.design.component.formatClock
import com.rumput.vision.core.design.component.formatTimeWithSeconds
import com.rumput.vision.core.design.component.localDate
import com.rumput.vision.core.design.theme.RumputTheme
import com.rumput.vision.core.design.theme.tabular
import com.rumput.vision.core.streaming.PlaybackFailure
import com.rumput.vision.core.streaming.PlaybackState
import com.rumput.vision.data.mapper.cameraDisplayName

@Composable
fun PlaybackScreen(onBack: () -> Unit, viewModel: PlaybackViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) { onStopOrDispose { viewModel.onStop() } }
    RumputScreen(
        title = state.cameraName ?: cameraDisplayName(state.camera),
        isTopLevel = false,
        onBack = onBack,
        modifier = Modifier.testTag("screen_playback"),
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 960.dp).fillMaxWidth()) {
                Text(
                    "${dayLabel(localDate(state.startEpochMillis))} · ${formatTimeWithSeconds(state.startEpochMillis)} – ${formatTimeWithSeconds(state.endEpochMillis)}",
                    style = MaterialTheme.typography.bodySmall.tabular,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = RumputTheme.spacing.gutter, vertical = RumputTheme.spacing.xs),
                )
                VideoArea(state, viewModel.player)
                Controls(state, viewModel)
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoArea(state: PlaybackUiState, player: Player?) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(state.aspectRatio)
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        PlayerSurface(player = player, surfaceType = SURFACE_TYPE_SURFACE_VIEW, modifier = Modifier.fillMaxSize())
        when (val playback = state.playback) {
            PlaybackState.Loading -> CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
            is PlaybackState.Ready -> if (playback.buffering) CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
            is PlaybackState.Error -> Text(
                stringResource(failureMessage(playback.failure)),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(RumputTheme.spacing.lg)
                    .background(Color.Black.copy(alpha = 0.7f), MaterialTheme.shapes.medium)
                    .padding(RumputTheme.spacing.lg)
                    .testTag("playback_error"),
            )
            else -> Unit
        }
    }
}

@Composable
private fun Controls(state: PlaybackUiState, viewModel: PlaybackViewModel) {
    val spacing = RumputTheme.spacing
    val ready = state.playback as? PlaybackState.Ready
    val duration = ready?.durationMillis ?: state.timeline?.totalMillis ?: 0L
    // While dragging, show the drag position instead of fighting it with playback updates.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val position = dragging?.toLong() ?: state.positionMillis
    val wallClock = state.timeline?.wallClockAt(position) ?: state.wallClockMillis

    Column(Modifier.padding(horizontal = spacing.gutter, vertical = spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                wallClock?.let { formatTimeWithSeconds(it) } ?: formatClock(position),
                style = MaterialTheme.typography.titleMedium.tabular,
                modifier = Modifier.weight(1f).testTag("playback_clock"),
            )
            Text(
                "${formatClock(position)} / ${formatClock(duration)}",
                style = MaterialTheme.typography.bodySmall.tabular,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { viewModel.seekTo(it.toLong()) }
                dragging = null
            },
            valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
            enabled = ready != null && duration > 0,
            modifier = Modifier.testTag("playback_slider"),
        )
        SegmentStrip(state)
        Row(
            Modifier.fillMaxWidth().padding(top = spacing.sm),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.seekBy(-SKIP_MILLIS) }, enabled = ready != null) {
                Icon(Icons.Filled.Replay10, stringResource(R.string.playback_back_10))
            }
            FilledIconButton(
                onClick = viewModel::togglePlay,
                enabled = ready != null || state.playback == PlaybackState.Ended,
                modifier = Modifier.padding(horizontal = spacing.lg).size(56.dp).testTag("playback_toggle"),
            ) {
                val playing = ready?.playing == true
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    stringResource(if (playing) R.string.playback_pause else R.string.playback_play),
                )
            }
            IconButton(onClick = { viewModel.seekBy(SKIP_MILLIS) }, enabled = ready != null) {
                Icon(Icons.Filled.Forward10, stringResource(R.string.playback_forward_10))
            }
        }
    }
}

/**
 * The requested window as a bar, with recorded segments highlighted. Recording here is event-triggered,
 * so this shows where the playable parts are within the hour or day.
 */
@Composable
private fun SegmentStrip(state: PlaybackUiState) {
    val segments = state.timeline?.segments ?: return
    val span = (state.endEpochMillis - state.startEpochMillis).coerceAtLeast(1)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        val width = maxWidth
        segments.forEach { segment ->
            val start = (segment.startEpochMillis - state.startEpochMillis).toFloat() / span
            val length = (segment.endEpochMillis - segment.startEpochMillis).toFloat() / span
            Box(
                Modifier
                    .offset(x = width * start)
                    .width((width * length).coerceAtLeast(2.dp))
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

private fun failureMessage(failure: PlaybackFailure) = when (failure) {
    PlaybackFailure.NOT_FOUND -> R.string.playback_not_found
    PlaybackFailure.UNAUTHORIZED -> R.string.playback_unauthorized
    PlaybackFailure.NETWORK -> R.string.playback_network
    PlaybackFailure.DECODER -> R.string.live_error_decoder
}

private const val SKIP_MILLIS = 10_000L
