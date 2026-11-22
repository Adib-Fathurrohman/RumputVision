package com.rumput.vision.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
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
import com.rumput.vision.core.design.component.ConnectionIndicator
import com.rumput.vision.core.design.component.EmptyState
import com.rumput.vision.core.design.component.LoadingState
import com.rumput.vision.core.design.component.RumputScreen
import com.rumput.vision.core.design.theme.RumputTheme
import com.rumput.vision.domain.model.ServerProfile

@Composable
fun DashboardScreen(onAddServer: () -> Unit, viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardContent(state = state, onAddServer = onAddServer)
}

@Composable
fun DashboardContent(state: DashboardUiState, onAddServer: () -> Unit) {
    RumputScreen(title = stringResource(R.string.nav_home), modifier = Modifier.testTag("screen_home")) { padding ->
        val contentModifier = Modifier.padding(padding).fillMaxSize()
        val profile = state.activeProfile
        when {
            state.isLoading -> LoadingState(contentModifier)
            profile == null -> EmptyState(
                icon = Icons.Outlined.Dns,
                title = stringResource(R.string.dashboard_no_server_title),
                message = stringResource(R.string.dashboard_no_server_body),
                actionLabel = stringResource(R.string.onboarding_add_server),
                onAction = onAddServer,
                modifier = contentModifier,
            )
            else -> Column(contentModifier) {
                ServerSummary(profile, state)
                EmptyState(
                    icon = Icons.Outlined.Videocam,
                    title = stringResource(R.string.dashboard_pending_title),
                    message = stringResource(R.string.dashboard_pending_body),
                )
            }
        }
    }
}

@Composable
private fun ServerSummary(profile: ServerProfile, state: DashboardUiState) {
    val spacing = RumputTheme.spacing
    Column(
        Modifier
            .padding(horizontal = spacing.gutter)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                profile.displayName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            ConnectionIndicator(state.connection)
        }
        profile.urls.forEach {
            Text(it.value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (profile.usesCleartext) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Icon(
                    Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = RumputTheme.statusColors.warning,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    stringResource(R.string.warning_cleartext_short),
                    style = MaterialTheme.typography.bodySmall,
                    color = RumputTheme.statusColors.warning,
                )
            }
        }
    }
}
