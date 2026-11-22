package com.rumput.vision.feature.events

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rumput.vision.R
import com.rumput.vision.core.design.component.EmptyState
import com.rumput.vision.core.design.component.ErrorState
import com.rumput.vision.core.design.component.EventCard
import com.rumput.vision.core.design.component.LoadingState
import com.rumput.vision.core.design.component.RumputScreen
import com.rumput.vision.core.design.component.SectionHeader
import com.rumput.vision.core.design.component.WarningText
import com.rumput.vision.core.design.component.dayLabel
import com.rumput.vision.core.design.component.frigateErrorMessage
import com.rumput.vision.core.design.component.localDate
import com.rumput.vision.core.design.theme.RumputTheme
import com.rumput.vision.data.mapper.cameraDisplayName
import com.rumput.vision.domain.model.labelDisplayName
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun EventsScreen(onOpenEvent: (String) -> Unit, viewModel: EventsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pickingDay by rememberSaveable { mutableStateOf(false) }

    RumputScreen(
        title = stringResource(R.string.nav_events),
        modifier = Modifier.testTag("screen_events"),
        actions = {
            IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Outlined.Refresh, stringResource(R.string.action_refresh)) }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Filters(state, viewModel, onPickDay = { pickingDay = true })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Timeline(state, viewModel, onOpenEvent)
        }
    }
    if (pickingDay) {
        DayPicker(
            initial = state.customDay,
            onPick = { viewModel.setCustomDay(it); pickingDay = false },
            onDismiss = { pickingDay = false },
        )
    }
}

@Composable
private fun Filters(state: EventsUiState, viewModel: EventsViewModel, onPickDay: () -> Unit) {
    val spacing = RumputTheme.spacing
    Column(Modifier.padding(vertical = spacing.xs)) {
        ChipRow {
            RangePreset.entries.filter { it != RangePreset.CUSTOM_DAY }.forEach { preset ->
                FilterChip(
                    selected = state.range == preset,
                    onClick = { viewModel.setRange(preset) },
                    label = { Text(stringResource(rangeLabel(preset))) },
                )
            }
            FilterChip(
                selected = state.range == RangePreset.CUSTOM_DAY,
                onClick = onPickDay,
                label = { Text(state.customDay?.takeIf { state.range == RangePreset.CUSTOM_DAY }?.let { dayLabel(it) } ?: stringResource(R.string.events_pick_day)) },
                modifier = Modifier.testTag("events_pick_day"),
            )
        }
        if (state.cameras.size > 1) {
            ChipRow {
                state.cameras.forEach { camera ->
                    FilterChip(
                        selected = camera.camera.name in state.selectedCameras,
                        onClick = { viewModel.toggleCamera(camera.camera.name) },
                        label = { Text(camera.camera.displayName) },
                    )
                }
            }
        }
        if (state.labels.size > 1) {
            ChipRow {
                state.labels.forEach { label ->
                    FilterChip(
                        selected = label in state.selectedLabels,
                        onClick = { viewModel.toggleLabel(label) },
                        label = { Text(labelDisplayName(label)) },
                    )
                }
            }
        }
        if (state.filterActive) {
            TextButton(onClick = viewModel::clearFilters, modifier = Modifier.padding(start = spacing.sm)) {
                Text(stringResource(R.string.events_clear_filters))
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = RumputTheme.spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(RumputTheme.spacing.sm),
    ) { content() }
}

@Composable
private fun Timeline(state: EventsUiState, viewModel: EventsViewModel, onOpenEvent: (String) -> Unit) {
    val modifier = Modifier.fillMaxSize()
    when {
        state.isLoading -> LoadingState(modifier)
        state.events.isEmpty() && state.error != null -> ErrorState(
            title = stringResource(R.string.events_load_failed),
            message = frigateErrorMessage(state.error),
            onRetry = { viewModel.refresh() },
            modifier = modifier,
        )
        state.events.isEmpty() -> EmptyState(
            icon = Icons.Outlined.NotificationsActive,
            title = stringResource(R.string.events_empty_title),
            message = stringResource(if (state.filterActive) R.string.events_empty_filtered else R.string.events_empty_body),
            modifier = modifier,
        )
        else -> EventList(state, viewModel, onOpenEvent)
    }
}

@Composable
private fun EventList(state: EventsUiState, viewModel: EventsViewModel, onOpenEvent: (String) -> Unit) {
    val listState = rememberLazyListState()
    // Load the next page when the last few rows come into view.
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearEnd, state.events.size) { if (nearEnd && !state.endReached) viewModel.loadMore() }

    val grouped = remember(state.events) { state.events.groupBy { localDate(it.startEpochMillis) } }
    val names = remember(state.cameras) { state.cameras.associate { it.camera.name to it.camera.displayName } }
    LazyColumn(Modifier.fillMaxSize().testTag("events_list"), state = listState) {
        if (state.showingCached) {
            item {
                WarningText(
                    stringResource(R.string.events_cached_notice),
                    Modifier.padding(horizontal = RumputTheme.spacing.gutter, vertical = RumputTheme.spacing.sm),
                )
            }
        }
        grouped.forEach { (day, dayEvents) ->
            item(key = "day_$day") { SectionHeader(dayLabel(day)) }
            items(dayEvents, key = { it.id }) { event ->
                EventCard(
                    event = event,
                    cameraName = names[event.camera] ?: cameraDisplayName(event.camera),
                    thumbnailUrl = viewModel.thumbnailUrl(event),
                    onClick = { onOpenEvent(event.id) },
                )
            }
        }
        if (state.isLoadingMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(RumputTheme.spacing.lg), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPicker(initial: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // DatePicker works in UTC millis; convert at the boundary only.
    val today = LocalDate.now()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) =
                Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= today
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) { DatePicker(state) }
}

private fun rangeLabel(preset: RangePreset) = when (preset) {
    RangePreset.ALL -> R.string.range_all
    RangePreset.TODAY -> R.string.range_today
    RangePreset.LAST_24_HOURS -> R.string.range_24h
    RangePreset.LAST_7_DAYS -> R.string.range_7d
    RangePreset.CUSTOM_DAY -> R.string.events_pick_day
}
