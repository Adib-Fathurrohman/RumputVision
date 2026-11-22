package com.rumput.vision.feature.liveview

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import com.rumput.vision.R
import com.rumput.vision.core.design.component.StatusLabel
import com.rumput.vision.core.design.component.previewAspectRatio
import com.rumput.vision.core.design.theme.RumputTheme
import com.rumput.vision.core.streaming.StreamFailure
import com.rumput.vision.core.streaming.StreamState
import com.rumput.vision.data.mapper.cameraDisplayName
import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.domain.model.StreamQuality
import kotlinx.coroutines.delay

@Composable
fun LiveViewScreen(onBack: () -> Unit, viewModel: LiveViewViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Stream only while the screen is visible: stopping closes the WebSocket (no background streaming).
    LifecycleStartEffect(viewModel) {
        viewModel.onStart()
        onStopOrDispose { viewModel.onStop() }
    }
    KeepScreenOn()
    ImmersiveInLandscape()

    LiveViewContent(
        state = state,
        player = viewModel.player,
        onBack = onBack,
        onRetry = viewModel::retry,
        onQuality = viewModel::setQuality,
        onToggleMute = viewModel::toggleMute,
        onSwitchCamera = viewModel::switchCamera,
    )
}

// PlayerSurface is marked @UnstableApi in media3-ui-compose 1.11.
@OptIn(UnstableApi::class)
@Composable
fun LiveViewContent(
    state: LiveViewUiState,
    player: Player?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onQuality: (StreamQuality) -> Unit,
    onToggleMute: () -> Unit,
    onSwitchCamera: (Int) -> Unit,
) {
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    val playing = state.stream is StreamState.Playing
    // Controls fade out while video plays; a tap brings them back.
    LaunchedEffect(controlsVisible, playing) {
        if (controlsVisible && playing) {
            delay(CONTROLS_TIMEOUT_MILLIS)
            controlsVisible = false
        }
    }
    val aspect = state.camera?.previewAspectRatio() ?: (16f / 9f)
    val title = state.camera?.camera?.displayName ?: cameraDisplayName(state.cameraName)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                controlsVisible = !controlsVisible
            }
            .testTag("screen_live_view"),
        contentAlignment = Alignment.Center,
    ) {
        // Sized to the camera's aspect ratio: sub streams such as 704×576 are anamorphic and are stretched back.
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            Box(Modifier.aspectRatio(aspect, matchHeightConstraintsFirst = isLandscape()).fillMaxWidth()) {
                PlayerSurface(player = player, surfaceType = SURFACE_TYPE_SURFACE_VIEW, modifier = Modifier.fillMaxSize())
            }
        }

        StreamOverlay(state, onRetry)

        if (state.suggestSubStream) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(top = 72.dp, start = RumputTheme.spacing.gutter, end = RumputTheme.spacing.gutter)
                    .background(Color.Black.copy(alpha = 0.75f), MaterialTheme.shapes.medium)
                    .padding(start = RumputTheme.spacing.md)
                    .testTag("suggest_sub"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.live_suggest_sub),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f, fill = false),
                )
                TextButton(onClick = { onQuality(StreamQuality.SUB) }) { Text(stringResource(R.string.live_switch_to_sub)) }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible || !playing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Controls(state, title, onBack, onQuality, onToggleMute, onSwitchCamera)
        }
    }
}

@Composable
private fun StreamOverlay(state: LiveViewUiState, onRetry: () -> Unit) {
    val stream = state.stream
    val notConnected = state.connection !is ConnectionState.Connected && stream !is StreamState.Playing
    when {
        notConnected && state.connection != ConnectionState.Connecting -> ErrorPanel(
            message = stringResource(R.string.live_error_not_connected),
            onRetry = onRetry,
        )
        stream is StreamState.Error -> ErrorPanel(
            message = failureMessage(stream.failure),
            detail = stream.retryInMillis?.let { stringResource(R.string.live_retrying, (it + 999) / 1000) },
            onRetry = onRetry,
        )
        stream is StreamState.Playing && stream.audioUnavailable -> Text(
            stringResource(R.string.live_audio_unavailable),
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.small)
                .padding(horizontal = RumputTheme.spacing.md, vertical = RumputTheme.spacing.xs),
        )
        stream == StreamState.Connecting || stream == StreamState.Buffering || state.connection == ConnectionState.Connecting ->
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(40.dp).testTag("live_loading"))
        else -> Unit
    }
}

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit, detail: String? = null) {
    Column(
        Modifier
            .widthIn(max = 360.dp)
            .background(Color.Black.copy(alpha = 0.7f), MaterialTheme.shapes.medium)
            .padding(RumputTheme.spacing.lg)
            .testTag("live_error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RumputTheme.spacing.sm),
    ) {
        Text(message, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        if (detail != null) Text(detail, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
        FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

@Composable
private fun Controls(
    state: LiveViewUiState,
    title: String,
    onBack: () -> Unit,
    onQuality: (StreamQuality) -> Unit,
    onToggleMute: () -> Unit,
    onSwitchCamera: (Int) -> Unit,
) {
    val spacing = RumputTheme.spacing
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                state.streamName?.let {
                    Text(it, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                }
            }
            StreamStatusLabel(state.stream)
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            if (state.canSwitchCamera) {
                IconButton(onClick = { onSwitchCamera(-1) }) {
                    Icon(Icons.Filled.ChevronLeft, stringResource(R.string.live_previous_camera), tint = Color.White)
                }
            }
            Spacer(Modifier.weight(1f))
            if (state.canSwitchQuality) {
                SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = 260.dp)) {
                    StreamQuality.entries.forEachIndexed { i, quality ->
                        SegmentedButton(
                            selected = state.quality == quality,
                            onClick = { onQuality(quality) },
                            shape = SegmentedButtonDefaults.itemShape(i, StreamQuality.entries.size),
                        ) {
                            Text(
                                stringResource(if (quality == StreamQuality.SUB) R.string.live_quality_sub else R.string.live_quality_main),
                                color = Color.White,
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onToggleMute) {
                Icon(
                    if (state.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    stringResource(if (state.muted) R.string.live_unmute else R.string.live_mute),
                    tint = Color.White,
                )
            }
            FullscreenToggle()
            Spacer(Modifier.weight(1f))
            if (state.canSwitchCamera) {
                IconButton(onClick = { onSwitchCamera(1) }) {
                    Icon(Icons.Filled.ChevronRight, stringResource(R.string.live_next_camera), tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun StreamStatusLabel(stream: StreamState) {
    val colors = RumputTheme.statusColors
    val (color, label) = when (stream) {
        is StreamState.Playing -> colors.live to stringResource(R.string.live_status_live)
        StreamState.Connecting -> colors.warning to stringResource(R.string.live_status_connecting)
        StreamState.Buffering -> colors.warning to stringResource(R.string.live_status_buffering)
        is StreamState.Error -> colors.offline to stringResource(R.string.live_status_error)
        StreamState.Idle, StreamState.Released -> colors.unknown to stringResource(R.string.live_status_stopped)
    }
    StatusLabel(color, label, Modifier.padding(end = RumputTheme.spacing.sm).testTag("live_status"))
}

@Composable
private fun failureMessage(failure: StreamFailure): String = stringResource(
    when (failure) {
        StreamFailure.UNAUTHORIZED -> R.string.live_error_unauthorized
        StreamFailure.SOURCE_ERROR -> R.string.live_error_source
        StreamFailure.UNSUPPORTED_CODEC -> R.string.live_error_codec
        StreamFailure.NETWORK -> R.string.live_error_network
        StreamFailure.FELL_BEHIND -> R.string.live_error_fell_behind
        StreamFailure.DECODER -> R.string.live_error_decoder
        StreamFailure.NOT_CONNECTED -> R.string.live_error_not_connected
    },
)

@Composable
private fun isLandscape() = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

/** Toggles a landscape-locked fullscreen; the activity's orientation is restored when the screen closes. */
@Composable
private fun FullscreenToggle() {
    val activity = LocalView.current.context as? Activity ?: return
    var locked by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(activity) {
        onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    IconButton(onClick = {
        locked = !locked
        activity.requestedOrientation =
            if (locked) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }) {
        Icon(
            if (locked) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
            stringResource(if (locked) R.string.live_exit_fullscreen else R.string.live_fullscreen),
            tint = Color.White,
        )
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun ImmersiveInLandscape() {
    val view = LocalView.current
    val landscape = isLandscape()
    DisposableEffect(view, landscape) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (landscape) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private const val CONTROLS_TIMEOUT_MILLIS = 4_000L
