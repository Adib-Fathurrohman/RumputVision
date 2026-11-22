package com.rumput.vision.feature.events

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.rumput.vision.R
import com.rumput.vision.core.design.component.ErrorState
import com.rumput.vision.core.design.component.LoadingState
import com.rumput.vision.core.design.component.RumputScreen
import com.rumput.vision.core.design.component.SectionHeader
import com.rumput.vision.core.design.component.dayLabel
import com.rumput.vision.core.design.component.formatDuration
import com.rumput.vision.core.design.component.formatTimeWithSeconds
import com.rumput.vision.core.design.component.frigateErrorMessage
import com.rumput.vision.core.design.component.localDate
import com.rumput.vision.core.design.theme.RumputTheme
import com.rumput.vision.core.design.theme.tabular
import com.rumput.vision.data.mapper.cameraDisplayName
import com.rumput.vision.domain.model.FrigateError
import com.rumput.vision.domain.model.labelDisplayName

@Composable
fun EventDetailScreen(
    onBack: () -> Unit,
    onPlayClip: (ClipWindow) -> Unit,
    onOpenLive: (String) -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val event = state.event
    RumputScreen(
        title = event?.let { labelDisplayName(it.subLabel ?: it.label) } ?: stringResource(R.string.event_title),
        isTopLevel = false,
        onBack = onBack,
        modifier = Modifier.testTag("screen_event_detail"),
    ) { padding ->
        val modifier = Modifier.padding(padding).fillMaxSize()
        when {
            state.isLoading -> LoadingState(modifier)
            event == null -> ErrorState(
                title = stringResource(
                    if (state.error is FrigateError.Http && (state.error as FrigateError.Http).code == 404) R.string.event_not_found else R.string.event_load_failed,
                ),
                message = state.error?.let { frigateErrorMessage(it) },
                onRetry = viewModel::load,
                modifier = modifier,
            )
            else -> Column(modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                val spacing = RumputTheme.spacing
                Column(Modifier.widthIn(max = 840.dp).fillMaxWidth()) {
                    Box(
                        Modifier
                            .padding(horizontal = spacing.gutter)
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        state.snapshotUrl?.let {
                            AsyncImage(
                                model = it,
                                contentDescription = stringResource(R.string.event_snapshot_description),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Column(Modifier.padding(spacing.gutter), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Button(
                            onClick = { onPlayClip(viewModel.clipWindow(event)) },
                            enabled = event.hasClip,
                            modifier = Modifier.fillMaxWidth().testTag("event_play_clip"),
                        ) {
                            Icon(Icons.Filled.PlayArrow, null)
                            Text(stringResource(if (event.hasClip) R.string.event_play_clip else R.string.event_no_clip))
                        }
                        OutlinedButton(onClick = { onOpenLive(event.camera) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.event_open_live))
                        }
                    }
                    SectionHeader(stringResource(R.string.event_section_details))
                    Info(stringResource(R.string.event_camera), state.cameraName ?: cameraDisplayName(event.camera))
                    Info(stringResource(R.string.event_label), labelDisplayName(event.label))
                    event.subLabel?.let { Info(stringResource(R.string.event_sub_label), it) }
                    Info(stringResource(R.string.event_date), dayLabel(localDate(event.startEpochMillis)))
                    Info(stringResource(R.string.event_started), formatTimeWithSeconds(event.startEpochMillis))
                    Info(
                        stringResource(R.string.event_duration),
                        event.durationMillis?.let { formatDuration(it) } ?: stringResource(R.string.event_in_progress),
                    )
                    if (event.zones.isNotEmpty()) Info(stringResource(R.string.event_zones), event.zones.joinToString(", "))
                    event.score?.let { Info(stringResource(R.string.event_score), "%.0f %%".format(it * 100)) }
                    if (event.retained) Info(stringResource(R.string.event_retained), stringResource(R.string.setting_on))
                }
            }
        }
    }
}

@Composable
private fun Info(label: String, value: String) {
    val spacing = RumputTheme.spacing
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = spacing.gutter, vertical = spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium.tabular)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
