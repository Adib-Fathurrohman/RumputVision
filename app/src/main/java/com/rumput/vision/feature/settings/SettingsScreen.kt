package com.rumput.vision.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rumput.vision.R
import com.rumput.vision.core.design.component.LoadingState
import com.rumput.vision.core.design.component.RumputScreen
import com.rumput.vision.core.design.component.SectionHeader
import com.rumput.vision.core.design.theme.RumputTheme
import com.rumput.vision.domain.model.AppPreferences
import com.rumput.vision.domain.model.ServerProfile
import com.rumput.vision.domain.model.StreamQuality

@Composable
fun SettingsScreen(
    onAddServer: () -> Unit,
    onEditServer: (String) -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RumputScreen(title = stringResource(R.string.nav_settings), modifier = Modifier.testTag("screen_settings")) { padding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@RumputScreen
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { SectionHeader(stringResource(R.string.settings_section_servers)) }
            items(state.profiles, key = { it.id }) { profile ->
                ProfileRow(
                    profile = profile,
                    onClick = { onEditServer(profile.id) },
                    onSetActive = { viewModel.setActiveProfile(profile.id) },
                )
            }
            item {
                NavigationRow(
                    title = stringResource(R.string.settings_add_server),
                    leading = { Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = onAddServer,
                    modifier = Modifier.testTag("settings_add_server"),
                )
            }

            item { SectionHeader(stringResource(R.string.settings_section_streaming)) }
            item { StreamingSettings(state.preferences, viewModel) }

            item { SectionHeader(stringResource(R.string.settings_section_notifications)) }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_in_app_updates),
                    subtitle = stringResource(R.string.settings_in_app_updates_body),
                    checked = state.preferences.inAppEventUpdates,
                    onCheckedChange = viewModel::setInAppEventUpdates,
                )
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_push_notifications),
                    subtitle = stringResource(R.string.settings_push_notifications_unavailable),
                    checked = false,
                    enabled = false,
                    onCheckedChange = {},
                )
            }

            item { SectionHeader(stringResource(R.string.settings_section_app)) }
            item {
                NavigationRow(
                    title = stringResource(R.string.settings_about),
                    leading = { Icon(Icons.Outlined.Info, contentDescription = null) },
                    onClick = onOpenAbout,
                )
            }
        }
    }
}

@Composable
private fun ProfileRow(profile: ServerProfile, onClick: () -> Unit, onSetActive: () -> Unit) {
    val spacing = RumputTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.gutter, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(profile.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                profile.urls.joinToString(" · ") { it.value },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            if (profile.usesCleartext) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Icon(Icons.Outlined.WarningAmber, null, tint = RumputTheme.statusColors.warning, modifier = Modifier.size(14.dp))
                    Text(
                        stringResource(R.string.warning_cleartext_short),
                        style = MaterialTheme.typography.labelSmall,
                        color = RumputTheme.statusColors.warning,
                    )
                }
            }
        }
        if (profile.isActive) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.settings_profile_active),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            TextButton(onClick = onSetActive) { Text(stringResource(R.string.settings_profile_set_active)) }
        }
    }
}

@Composable
private fun StreamingSettings(prefs: AppPreferences, viewModel: SettingsViewModel) {
    val spacing = RumputTheme.spacing
    Column(Modifier.padding(horizontal = spacing.gutter), verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        LabeledSegments(
            label = stringResource(R.string.settings_grid_quality),
            options = StreamQuality.entries,
            selected = prefs.gridStreamQuality,
            optionLabel = { qualityLabel(it) },
            onSelect = viewModel::setGridQuality,
        )
        LabeledSegments(
            label = stringResource(R.string.settings_fullscreen_quality),
            options = StreamQuality.entries,
            selected = prefs.fullscreenStreamQuality,
            optionLabel = { qualityLabel(it) },
            onSelect = viewModel::setFullscreenQuality,
        )
        LabeledSegments(
            label = stringResource(R.string.settings_max_grid_streams),
            options = (AppPreferences.MIN_GRID_STREAMS..AppPreferences.MAX_GRID_STREAMS).toList(),
            selected = prefs.maxConcurrentGridStreams,
            optionLabel = { it.toString() },
            onSelect = viewModel::setMaxGridStreams,
        )
    }
    SwitchRow(
        title = stringResource(R.string.settings_start_muted),
        subtitle = null,
        checked = prefs.startMuted,
        onCheckedChange = viewModel::setStartMuted,
    )
}

@Composable
private fun qualityLabel(quality: StreamQuality): String = stringResource(
    when (quality) {
        StreamQuality.SUB -> R.string.quality_sub
        StreamQuality.MAIN -> R.string.quality_main
    },
)

@Composable
internal fun <T> LabeledSegments(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(RumputTheme.spacing.sm)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) { Text(optionLabel(option), maxLines = 1) }
            }
        }
    }
}

@Composable
internal fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val spacing = RumputTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = spacing.gutter, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            val color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun NavigationRow(
    title: String,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = RumputTheme.spacing
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = spacing.gutter, vertical = spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            leading()
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
