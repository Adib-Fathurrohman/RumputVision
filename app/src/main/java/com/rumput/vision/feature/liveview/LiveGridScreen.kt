package com.rumput.vision.feature.liveview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import com.rumput.vision.R
import com.rumput.vision.core.design.component.CameraSnapshot
import com.rumput.vision.core.design.component.EmptyState
import com.rumput.vision.core.design.component.LoadingState
import com.rumput.vision.core.design.component.RumputScreen
import com.rumput.vision.core.design.component.StatusDot
import com.rumput.vision.core.design.component.previewAspectRatio
import com.rumput.vision.core.design.theme.RumputTheme
import com.rumput.vision.core.streaming.StreamFailure
import com.rumput.vision.core.streaming.StreamState

@Composable
fun LiveGridScreen(
    onBack: () -> Unit,
    onOpenCamera: (String) -> Unit,
    viewModel: LiveGridViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) {
        viewModel.onStart()
        onStopOrDispose { viewModel.onStop() }
    }
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    RumputScreen(
        title = stringResource(R.string.live_grid_title),
        isTopLevel = false,
        onBack = onBack,
        modifier = Modifier.testTag("screen_live_grid"),
        actions = {
            if (state.pageCount > 1) {
                IconButton(onClick = viewModel::previousPage, enabled = state.page > 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.live_grid_previous_page))
                }
                Text("${state.page + 1}/${state.pageCount}", style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = viewModel::nextPage, enabled = state.page < state.pageCount - 1) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.live_grid_next_page))
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding).fillMaxSize()
        when {
            state.isLoading -> LoadingState(modifier)
            state.tiles.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Videocam,
                title = stringResource(R.string.cameras_empty_title),
                message = null,
                modifier = modifier,
            )
            else -> Grid(state, viewModel::player, onOpenCamera, viewModel::retry, modifier)
        }
    }
}

/** 2 columns; tiles are sized to fit both width and height so 2×2 also fits a landscape phone. */
@Composable
private fun Grid(
    state: LiveGridUiState,
    player: (Int) -> Player?,
    onOpenCamera: (String) -> Unit,
    onRetry: (Int) -> Unit,
    modifier: Modifier,
) {
    val spacing = RumputTheme.spacing
    BoxWithConstraints(modifier.padding(spacing.sm), contentAlignment = Alignment.Center) {
        val columns = 2
        val rows = (state.tiles.size + 1) / 2
        val aspect = state.tiles.first().camera.previewAspectRatio()
        val gap = spacing.sm
        val tileWidth = min(
            (maxWidth - gap * (columns - 1)) / columns,
            (maxHeight - gap * (rows - 1)) / rows * aspect,
        )
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            state.tiles.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { tile ->
                        Tile(
                            tile = tile,
                            player = tile.slot?.let(player),
                            refreshKey = state.snapshotTick,
                            onClick = { onOpenCamera(tile.camera.camera.name) },
                            onRetry = { tile.slot?.let(onRetry) },
                            modifier = Modifier.size(tileWidth, tileWidth / aspect),
                        )
                    }
                }
            }
        }
    }
}

// PlayerSurface is marked @UnstableApi in media3-ui-compose 1.11.
@OptIn(UnstableApi::class)
@Composable
private fun Tile(
    tile: GridTile,
    player: Player?,
    refreshKey: Long,
    onClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    val spacing = RumputTheme.spacing
    val playing = tile.stream is StreamState.Playing
    val stream = tile.stream
    // After the first frame the surface keeps showing the last frame during a stall; covering it with a
    // still (which may not load on a congested link either) made tiles go blank.
    val hasVideo = playing || stream == StreamState.Buffering
    Box(
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(Color.Black)
            .clickable(onClickLabel = stringResource(R.string.live_grid_open_camera), onClick = onClick)
            .testTag("grid_tile_${tile.camera.camera.name}"),
    ) {
        // The surface must exist before the first frame can render, so it is always composed for live slots.
        if (player != null) {
            PlayerSurface(player = player, surfaceType = SURFACE_TYPE_SURFACE_VIEW, modifier = Modifier.fillMaxSize())
        }
        // Until video plays (or when a slot is not live, or decoding failed) a refreshing still covers the cell.
        if (!hasVideo) {
            CameraSnapshot(url = tile.snapshotUrl, refreshKey = refreshKey, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = spacing.sm, vertical = spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            StatusDot(
                when {
                    playing -> RumputTheme.statusColors.live
                    stream == StreamState.Buffering -> RumputTheme.statusColors.warning
                    stream is StreamState.Error -> RumputTheme.statusColors.offline
                    tile.slot == null -> RumputTheme.statusColors.unknown
                    else -> RumputTheme.statusColors.warning
                },
            )
            Text(
                tile.camera.camera.displayName,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(
                    when {
                        playing -> R.string.live_status_live
                        stream == StreamState.Buffering -> R.string.live_status_buffering
                        stream is StreamState.Error -> R.string.live_status_error
                        tile.slot == null -> R.string.live_grid_still
                        else -> R.string.live_status_connecting
                    },
                ),
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        when {
            stream is StreamState.Error && stream.retryInMillis == null && stream.failure != StreamFailure.FELL_BEHIND ->
                IconButton(onClick = onRetry, modifier = Modifier.align(Alignment.Center)) {
                    Icon(Icons.Filled.Refresh, stringResource(R.string.action_retry), tint = Color.White)
                }
            tile.slot != null && (stream == StreamState.Connecting || stream == StreamState.Buffering) ->
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(spacing.sm).size(16.dp),
                )
            else -> Unit
        }
    }
}
