package com.rumput.vision.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rumput.vision.R
import com.rumput.vision.core.design.component.ErrorState
import com.rumput.vision.core.design.component.LoadingState
import com.rumput.vision.core.design.component.RumputScreen
import com.rumput.vision.core.design.theme.RumputTheme
import com.rumput.vision.domain.model.AuthMode
import com.rumput.vision.domain.model.EndpointPreference
import com.rumput.vision.domain.usecase.UrlError
import com.rumput.vision.domain.usecase.UrlWarning

@Composable
fun ServerProfileEditScreen(
    onBack: () -> Unit,
    onFinished: () -> Unit,
    viewModel: ServerProfileEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }

    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(if (state.isNew) R.string.profile_add_title else R.string.profile_edit_title)

    RumputScreen(
        title = title,
        isTopLevel = false,
        onBack = onBack,
        modifier = Modifier.testTag("screen_profile_edit"),
        actions = {
            if (!state.isNew && !state.notFound) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.profile_delete))
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            state.notFound -> ErrorState(
                title = stringResource(R.string.profile_not_found),
                message = null,
                modifier = Modifier.padding(padding),
            )
            else -> ProfileForm(state, viewModel, Modifier.padding(padding))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.profile_delete_confirm_title)) },
            text = { Text(stringResource(R.string.profile_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.profile_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ProfileForm(state: ServerProfileEditUiState, viewModel: ServerProfileEditViewModel, modifier: Modifier) {
    val spacing = RumputTheme.spacing
    val draft = state.draft
    val v = state.validation
    val showErrors = state.showErrors

    Column(
        modifier = modifier
            .fillMaxSize()
            // No imePadding(): RumputScreen's safeDrawing insets already include the keyboard.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.gutter, vertical = spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            FormField(
                value = draft.displayName,
                onValueChange = viewModel::onDisplayNameChange,
                label = stringResource(R.string.profile_field_name),
                error = if (showErrors) v.nameError?.let { nameErrorText(it) } else null,
                testTag = "field_name",
            )
            UrlField(
                value = draft.localUrl,
                onValueChange = viewModel::onLocalUrlChange,
                label = stringResource(R.string.profile_field_local_url),
                placeholder = "http://frigate.home.arpa",
                error = if (showErrors) v.localUrlError else null,
                warnings = v.localUrlWarnings,
                testTag = "field_local_url",
            )
            UrlField(
                value = draft.remoteUrl,
                onValueChange = viewModel::onRemoteUrlChange,
                label = stringResource(R.string.profile_field_remote_url),
                placeholder = "https://frigate.tailnet-name.ts.net",
                error = if (showErrors) v.remoteUrlError else null,
                warnings = v.remoteUrlWarnings,
                testTag = "field_remote_url",
            )
            LabeledSegments(
                label = stringResource(R.string.profile_field_endpoint),
                options = EndpointPreference.entries,
                selected = draft.endpointPreference,
                optionLabel = { endpointLabel(it) },
                onSelect = viewModel::onEndpointPreferenceChange,
            )
            if (showErrors && v.formError != null) {
                Text(formErrorText(v.formError), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            LabeledSegments(
                label = stringResource(R.string.profile_field_auth),
                options = AuthMode.entries,
                selected = draft.authMode,
                optionLabel = { authLabel(it) },
                onSelect = viewModel::onAuthModeChange,
            )
            when (draft.authMode) {
                AuthMode.FRIGATE_LOGIN -> LoginFields(state, viewModel)
                AuthMode.NONE -> WarningLine(stringResource(R.string.profile_auth_none_warning))
            }

            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().testTag("profile_save"),
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}

@Composable
private fun LoginFields(state: ServerProfileEditUiState, viewModel: ServerProfileEditViewModel) {
    val draft = state.draft
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    FormField(
        value = draft.username,
        onValueChange = viewModel::onUsernameChange,
        label = stringResource(R.string.profile_field_username),
        error = if (state.showErrors && state.validation.usernameRequired) stringResource(R.string.error_username_required) else null,
        testTag = "field_username",
    )
    // Text lives in Compose state; the ViewModel receives every change but never restores it.
    val passwordState = rememberTextFieldState()
    LaunchedEffect(passwordState) {
        snapshotFlow { passwordState.text.toString() }.collect(viewModel::onPasswordChange)
    }
    OutlinedSecureTextField(
        state = passwordState,
        label = { Text(stringResource(R.string.profile_field_password)) },
        supportingText = {
            Text(
                stringResource(
                    if (state.hasStoredPassword) R.string.profile_password_keep_hint else R.string.profile_password_hint,
                ),
            )
        },
        textObfuscationMode = if (passwordVisible) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(if (passwordVisible) R.string.action_hide_password else R.string.action_show_password),
                )
            }
        },
        modifier = Modifier.fillMaxWidth().testTag("field_password"),
    )
    SwitchRow(
        title = stringResource(R.string.profile_remember_password),
        subtitle = stringResource(R.string.profile_remember_password_body),
        checked = draft.rememberPassword,
        onCheckedChange = viewModel::onRememberPasswordChange,
        modifier = Modifier.padding(horizontal = 0.dp),
    )
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    testTag: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    )
}

@Composable
private fun UrlField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    error: UrlError?,
    warnings: Set<UrlWarning>,
    testTag: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RumputTheme.spacing.xs)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            isError = error != null,
            supportingText = error?.let { { Text(urlErrorText(it)) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth().testTag(testTag),
        )
        warnings.sorted().forEach { WarningLine(urlWarningText(it)) }
    }
}

@Composable
private fun WarningLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(RumputTheme.spacing.sm)) {
        Icon(
            Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = RumputTheme.statusColors.warning,
            modifier = Modifier.size(16.dp).padding(top = 2.dp),
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = RumputTheme.statusColors.warning)
    }
}
