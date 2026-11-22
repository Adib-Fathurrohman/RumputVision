package com.rumput.vision.feature.recordings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rumput.vision.R
import com.rumput.vision.core.design.component.EmptyState
import com.rumput.vision.core.design.component.ErrorState
import com.rumput.vision.core.design.component.LoadingState
import com.rumput.vision.core.design.component.RumputScreen
import com.rumput.vision.core.design.component.SectionHeader
import com.rumput.vision.core.design.component.dayLabel
import com.rumput.vision.core.design.component.formatDuration
import com.rumput.vision.core.design.component.frigateErrorMessage
import com.rumput.vision.core.design.theme.RumputTheme
import com.rumput.vision.core.design.theme.tabular
import com.rumput.vision.domain.model.RecordingHour
import java.time.LocalDate

@Composable
fun RecordingsScreen(onPlay: (PlaybackWindow) -> Unit, viewModel: RecordingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = RumputTheme.spacing
    RumputScreen(title = stringResource(R.string.nav_recordings), modifier = Modifier.testTag("screen_recordings")) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ChipRow {
                state.cameras.forEach { camera ->
                    FilterChip(
                        selected = camera.camera.name == state.selectedCamera,
                        onClick = { viewModel.selectCamera(camera.camera.name) },
                        label = { Text(camera.camera.displayName) },
                    )
                }
            }
            if (state.days.isNotEmpty()) {
                ChipRow {
                    state.days.forEach { day ->
                        FilterChip(
                            selected = day.date == state.selectedDay,
                            onClick = { viewModel.selectDay(day.date) },
                            label = { Text(dayLabel(LocalDate.parse(day.date))) },
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val modifier = Modifier.fillMaxSize()
            val day = state.day
            when {
                state.isLoading -> LoadingState(modifier)
                state.error != null -> ErrorState(
                    title = stringResource(R.string.recordings_load_failed),
                    message = frigateErrorMessage(state.error!!),
                    onRetry = viewModel::retry,
                    modifier = modifier,
                )
                day == null -> EmptyState(
                    icon = Icons.Outlined.VideoLibrary,
                    title = stringResource(R.string.recordings_empty_title),
                    message = stringResource(R.string.recordings_empty_body),
                    modifier = modifier,
                )
                else -> LazyColumn(modifier.testTag("recording_hours")) {
                    item {
                        SectionHeader(
                            pluralStringResource(R.plurals.recordings_day_summary, day.events, day.events),
                        ) {
                            TextButton(onClick = { viewModel.dayWindow(day.date)?.let(onPlay) }) {
                                Text(stringResource(R.string.recordings_play_day))
                            }
                        }
                    }
                    items(day.hours.sortedByDescending { it.hour }, key = { it.hour }) { hour ->
                        HourRow(hour) { viewModel.hourWindow(day.date, hour.hour)?.let(onPlay) }
                    }
                }
            }
        }
    }
}

/** One hour: how much of it was recorded, as text and as a bar. */
@Composable
private fun HourRow(hour: RecordingHour, onClick: () -> Unit) {
    val spacing = RumputTheme.spacing
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.recordings_play_hour), onClick = onClick)
            .padding(horizontal = spacing.gutter, vertical = spacing.md)
            .testTag("hour_${hour.hour}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text("%02d:00".format(hour.hour), style = MaterialTheme.typography.titleSmall.tabular, modifier = Modifier.width(56.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((hour.recordedSeconds / 3600.0).toFloat().coerceIn(0.01f, 1f))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            Text(
                stringResource(R.string.recordings_hour_detail, formatDuration((hour.recordedSeconds * 1000).toLong())) +
                    if (hour.events > 0) " · " + pluralStringResource(R.plurals.recordings_events, hour.events, hour.events) else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = RumputTheme.spacing.gutter, vertical = RumputTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(RumputTheme.spacing.sm),
    ) { content() }
}
