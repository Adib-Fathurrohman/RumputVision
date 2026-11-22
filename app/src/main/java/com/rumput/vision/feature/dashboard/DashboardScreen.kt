package com.rumput.vision.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rumput.vision.R
import com.rumput.vision.core.design.component.EmptyState
import com.rumput.vision.core.design.component.LoadingState
import com.rumput.vision.core.design.component.RumputScreen
import com.rumput.vision.core.design.component.SectionHeader
import com.rumput.vision.core.design.component.ServerStatusCard
import com.rumput.vision.core.design.component.WarningText
import com.rumput.vision.core.design.component.frigateErrorMessage
import com.rumput.vision.core.design.theme.RumputTheme
import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.domain.model.UnauthorizedReason
import com.rumput.vision.domain.repository.SignInResult
import com.rumput.vision.feature.cameras.CameraRow
import com.rumput.vision.feature.settings.SwitchRow

@Composable
fun DashboardScreen(
    onAddServer: () -> Unit,
    onEditServer: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardContent(
        state = state,
        onAddServer = onAddServer,
        onEditServer = onEditServer,
        onRetry = viewModel::retry,
        onSignIn = viewModel::signIn,
    )
}

@Composable
fun DashboardContent(
    state: DashboardUiState,
    onAddServer: () -> Unit,
    onEditServer: (String) -> Unit,
    onRetry: () -> Unit,
    onSignIn: (String, Boolean) -> Unit,
) {
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
            else -> LazyColumn(contentModifier) {
                item {
                    ServerStatusCard(
                        serverName = profile.displayName,
                        connection = state.connection,
                        status = state.serverStatus,
                        usesCleartext = profile.usesCleartext,
                        modifier = Modifier.padding(horizontal = RumputTheme.spacing.gutter),
                    )
                }
                item {
                    ConnectionBody(
                        state = state,
                        onRetry = onRetry,
                        onSignIn = onSignIn,
                        onEditServer = { onEditServer(profile.id) },
                    )
                }
                if (state.cameras.isNotEmpty()) {
                    item {
                        SectionHeader(
                            stringResource(R.string.dashboard_cameras_header, state.cameras.size, state.onlineCount),
                        )
                    }
                    if (state.showingCachedData) {
                        item {
                            WarningText(
                                stringResource(R.string.cameras_cached_notice),
                                Modifier.padding(horizontal = RumputTheme.spacing.gutter, vertical = RumputTheme.spacing.xs),
                            )
                        }
                    }
                    items(state.cameras, key = { it.camera.name }) { CameraRow(it) }
                }
            }
        }
    }
}

@Composable
private fun ConnectionBody(
    state: DashboardUiState,
    onRetry: () -> Unit,
    onSignIn: (String, Boolean) -> Unit,
    onEditServer: () -> Unit,
) {
    val spacing = RumputTheme.spacing
    val modifier = Modifier
        .padding(horizontal = spacing.gutter, vertical = spacing.md)
        .fillMaxWidth()
    when (val connection = state.connection) {
        ConnectionState.Connecting, ConnectionState.Unknown -> Column(modifier) {
            CircularProgressIndicator(Modifier.padding(spacing.sm), strokeWidth = 3.dp)
        }
        is ConnectionState.Unreachable -> MessageCard(
            title = stringResource(R.string.dashboard_unreachable_title),
            body = frigateErrorMessage(connection.error),
            modifier = modifier,
        ) { OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) } }
        is ConnectionState.Unauthorized -> when (connection.reason) {
            UnauthorizedReason.SERVER_REQUIRES_LOGIN -> MessageCard(
                title = stringResource(R.string.dashboard_server_requires_login_title),
                body = stringResource(R.string.dashboard_server_requires_login_body),
                modifier = modifier,
            ) { OutlinedButton(onClick = onEditServer) { Text(stringResource(R.string.profile_edit_title)) } }
            else -> SignInCard(connection.reason, state.signIn, onSignIn, modifier)
        }
        is ConnectionState.Connected, ConnectionState.NotConfigured -> Unit
    }
}

@Composable
private fun SignInCard(
    reason: UnauthorizedReason,
    signIn: SignInUiState,
    onSignIn: (String, Boolean) -> Unit,
    modifier: Modifier,
) {
    val passwordState = rememberTextFieldState()
    var remember by rememberSaveable { mutableStateOf(true) }
    val error = when (val result = signIn.result) {
        SignInResult.InvalidCredentials -> stringResource(R.string.sign_in_invalid)
        SignInResult.RateLimited -> stringResource(R.string.error_rate_limited)
        is SignInResult.Failed -> frigateErrorMessage(result.error)
        else -> when (reason) {
            UnauthorizedReason.INVALID_CREDENTIALS -> stringResource(R.string.sign_in_saved_password_rejected)
            UnauthorizedReason.RATE_LIMITED -> stringResource(R.string.error_rate_limited)
            else -> null
        }
    }
    MessageCard(
        title = stringResource(R.string.sign_in_title),
        body = stringResource(R.string.sign_in_body),
        modifier = modifier.testTag("sign_in_card"),
    ) {
        OutlinedSecureTextField(
            state = passwordState,
            label = { Text(stringResource(R.string.profile_field_password)) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            textObfuscationMode = TextObfuscationMode.Hidden,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        SwitchRow(
            title = stringResource(R.string.profile_remember_password),
            subtitle = null,
            checked = remember,
            onCheckedChange = { remember = it },
        )
        Button(
            onClick = { onSignIn(passwordState.text.toString(), remember) },
            enabled = !signIn.inProgress,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.sign_in_action)) }
    }
}

@Composable
private fun MessageCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val spacing = RumputTheme.spacing
    Column(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}
