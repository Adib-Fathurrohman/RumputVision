package com.rumput.vision.feature.cameras

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rumput.vision.R
import com.rumput.vision.core.design.component.EmptyState
import com.rumput.vision.core.design.component.ErrorState
import com.rumput.vision.core.design.component.LoadingState
import com.rumput.vision.core.design.component.RumputScreen
import com.rumput.vision.core.design.component.WarningText
import com.rumput.vision.core.design.component.frigateErrorMessage
import com.rumput.vision.core.design.theme.RumputTheme
import com.rumput.vision.domain.model.ConnectionState

@Composable
fun CamerasScreen(viewModel: CamerasViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CamerasContent(state, onRefresh = viewModel::refresh)
}

@Composable
fun CamerasContent(state: CamerasUiState, onRefresh: () -> Unit) {
    RumputScreen(
        title = stringResource(R.string.nav_cameras),
        modifier = Modifier.testTag("screen_cameras"),
        actions = {
            IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.action_refresh))
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding).fillMaxSize()
        when {
            state.isLoading -> LoadingState(modifier)
            state.connection == ConnectionState.NotConfigured -> EmptyState(
                icon = Icons.Outlined.Videocam,
                title = stringResource(R.string.dashboard_no_server_title),
                message = stringResource(R.string.dashboard_no_server_body),
                modifier = modifier,
            )
            state.cameras.isEmpty() && state.lastError != null -> ErrorState(
                title = stringResource(R.string.cameras_load_failed),
                message = frigateErrorMessage(state.lastError),
                onRetry = onRefresh,
                modifier = modifier,
            )
            state.cameras.isEmpty() && state.isRefreshing -> LoadingState(modifier)
            state.cameras.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Videocam,
                title = stringResource(R.string.cameras_empty_title),
                message = stringResource(R.string.cameras_empty_body),
                modifier = modifier,
            )
            else -> LazyColumn(modifier) {
                if (state.isRefreshing) {
                    item { LinearProgressIndicator(Modifier.padding(horizontal = RumputTheme.spacing.gutter)) }
                }
                if (state.showingCachedData) {
                    item {
                        WarningText(
                            stringResource(R.string.cameras_cached_notice),
                            Modifier.padding(horizontal = RumputTheme.spacing.gutter, vertical = RumputTheme.spacing.sm),
                        )
                    }
                }
                items(state.cameras, key = { it.camera.name }) { item ->
                    CameraRow(item)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
