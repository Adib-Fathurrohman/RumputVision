package com.rumput.vision.feature.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rumput.vision.core.common.Clock
import com.rumput.vision.domain.model.CameraWithStatus
import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.domain.model.Event
import com.rumput.vision.domain.model.EventFilter
import com.rumput.vision.domain.model.FrigateError
import com.rumput.vision.domain.model.FrigateResult
import com.rumput.vision.domain.repository.CameraMedia
import com.rumput.vision.domain.repository.EventRepository
import com.rumput.vision.domain.repository.ServerConnection
import com.rumput.vision.domain.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Date range choices. [CUSTOM_DAY] uses [EventsUiState.customDay]. */
enum class RangePreset { ALL, TODAY, LAST_24_HOURS, LAST_7_DAYS, CUSTOM_DAY }

data class EventsUiState(
    val events: List<Event> = emptyList(),
    val cameras: List<CameraWithStatus> = emptyList(),
    val labels: List<String> = emptyList(),
    val selectedCameras: Set<String> = emptySet(),
    val selectedLabels: Set<String> = emptySet(),
    val range: RangePreset = RangePreset.ALL,
    val customDay: LocalDate? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    /** Showing cached events because the server could not be reached. */
    val showingCached: Boolean = false,
    val error: FrigateError? = null,
    val connection: ConnectionState = ConnectionState.Unknown,
) {
    val filterActive: Boolean get() = selectedCameras.isNotEmpty() || selectedLabels.isNotEmpty() || range != RangePreset.ALL
}

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val events: EventRepository,
    serverRepository: ServerRepository,
    private val connection: ServerConnection,
    private val media: CameraMedia,
    private val clock: Clock,
) : ViewModel() {

    private data class Selection(
        val cameras: Set<String> = emptySet(),
        val labels: Set<String> = emptySet(),
        val range: RangePreset = RangePreset.ALL,
        val customDay: LocalDate? = null,
    )

    /** Pages loaded from the server for one selection. */
    private data class Loaded(
        val selection: Selection,
        val events: List<Event>,
        val nextBefore: Long?,
        val error: FrigateError?,
        val loading: Boolean,
        val loadingMore: Boolean,
    )

    private val selection = MutableStateFlow(Selection())
    private val loaded = MutableStateFlow<Loaded?>(null)
    private val labels = MutableStateFlow<List<String>>(emptyList())
    private var loadJob: Job? = null

    fun thumbnailUrl(event: Event): String? = if (event.hasSnapshot) media.eventThumbnailUrl(event.id) else null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val cached: Flow<List<Event>> = selection.flatMapLatest { events.observeCachedEvents(filterFor(it), CACHE_PAGE) }

    /** While observed: realtime event activity reloads the first page. Emits nothing itself. */
    private val realtimeRefresh: Flow<Nothing> = events.eventActivity
        .onEach { refresh(merge = true) }
        .transform { }

    val uiState: StateFlow<EventsUiState> = merge(
        combine(
            combine(selection, loaded, cached, ::Triple),
            serverRepository.observeCameras(),
            labels,
            connection.state,
        ) { (sel, load, cachedEvents), cameras, labelList, conn ->
            val current = load?.takeIf { it.selection == sel }
            val useRemote = current != null && current.error == null && (current.events.isNotEmpty() || !current.loading)
            EventsUiState(
                events = if (useRemote) current.events else cachedEvents,
                cameras = cameras,
                labels = labelList,
                selectedCameras = sel.cameras,
                selectedLabels = sel.labels,
                range = sel.range,
                customDay = sel.customDay,
                isLoading = current?.loading != false && cachedEvents.isEmpty(),
                isLoadingMore = current?.loadingMore == true,
                endReached = useRemote && current.nextBefore == null,
                showingCached = !useRemote && current?.error != null && cachedEvents.isNotEmpty(),
                error = current?.error,
                connection = conn,
            )
        },
        realtimeRefresh,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventsUiState())

    init {
        refresh()
        viewModelScope.launch { (events.labels() as? FrigateResult.Success)?.let { labels.value = it.value } }
    }

    fun toggleCamera(camera: String) = select { copy(cameras = cameras.toggle(camera)) }

    fun toggleLabel(label: String) = select { copy(labels = labels.toggle(label)) }

    fun setRange(range: RangePreset) = select { copy(range = range) }

    fun setCustomDay(day: LocalDate) = select { copy(range = RangePreset.CUSTOM_DAY, customDay = day) }

    fun clearFilters() = select { Selection() }

    /** Reloads the first page. With [merge], keeps already-loaded older pages (realtime updates). */
    fun refresh(merge: Boolean = false) {
        val sel = selection.value
        loadJob?.cancel()
        loaded.update { prev ->
            val keep = prev?.takeIf { it.selection == sel }
            Loaded(sel, keep?.events.orEmpty(), keep?.nextBefore, null, loading = true, loadingMore = false)
        }
        loadJob = viewModelScope.launch {
            when (val result = events.fetchEvents(filterFor(sel), beforeEpochMillis = null, limit = PAGE_SIZE)) {
                is FrigateResult.Success -> loaded.update { prev ->
                    val older = if (merge && prev?.selection == sel) prev.events else emptyList()
                    val mergedEvents = (result.value.events + older).distinctBy { it.id }.sortedByDescending { it.startEpochMillis }
                    Loaded(
                        selection = sel,
                        events = mergedEvents,
                        nextBefore = if (merge && older.isNotEmpty()) prev?.nextBefore else result.value.nextBeforeEpochMillis,
                        error = null,
                        loading = false,
                        loadingMore = false,
                    )
                }
                is FrigateResult.Failure -> loaded.update { prev ->
                    (prev ?: Loaded(sel, emptyList(), null, null, false, false)).copy(error = result.error, loading = false)
                }
            }
        }
    }

    fun loadMore() {
        val current = loaded.value ?: return
        val before = current.nextBefore ?: return
        if (current.loading || current.loadingMore || current.selection != selection.value) return
        loaded.value = current.copy(loadingMore = true)
        loadJob = viewModelScope.launch {
            when (val result = events.fetchEvents(filterFor(current.selection), before, PAGE_SIZE)) {
                is FrigateResult.Success -> loaded.update {
                    current.copy(
                        events = (current.events + result.value.events).distinctBy { e -> e.id },
                        nextBefore = result.value.nextBeforeEpochMillis,
                        loadingMore = false,
                    )
                }
                is FrigateResult.Failure -> loaded.update { current.copy(loadingMore = false, error = result.error) }
            }
        }
    }

    private fun select(change: Selection.() -> Selection) {
        val next = selection.value.change()
        if (next == selection.value) return
        selection.value = next
        refresh()
    }

    private fun filterFor(sel: Selection): EventFilter {
        val zone = ZoneId.systemDefault()
        val now = clock.nowEpochMillis()
        val (after, before) = when (sel.range) {
            RangePreset.ALL -> null to null
            RangePreset.TODAY -> LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli() to null
            RangePreset.LAST_24_HOURS -> now - DAY_MILLIS to null
            RangePreset.LAST_7_DAYS -> now - 7 * DAY_MILLIS to null
            RangePreset.CUSTOM_DAY -> sel.customDay?.let { day ->
                day.atStartOfDay(zone).toInstant().toEpochMilli() to day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            } ?: (null to null)
        }
        return EventFilter(sel.cameras, sel.labels, after, before)
    }

    private fun Set<String>.toggle(value: String) = if (value in this) this - value else this + value

    companion object {
        const val PAGE_SIZE = 50
        private const val CACHE_PAGE = 200
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}
