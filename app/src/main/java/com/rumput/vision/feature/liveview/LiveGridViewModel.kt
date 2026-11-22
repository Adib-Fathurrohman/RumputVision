package com.rumput.vision.feature.liveview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.rumput.vision.core.common.ticker
import com.rumput.vision.core.streaming.BufferProfile
import com.rumput.vision.core.streaming.CameraStreamPlayer
import com.rumput.vision.core.streaming.CameraStreamPlayerFactory
import com.rumput.vision.core.streaming.StreamSource
import com.rumput.vision.core.streaming.StreamState
import com.rumput.vision.domain.model.AppPreferences
import com.rumput.vision.domain.model.CameraStatus
import com.rumput.vision.domain.model.CameraWithStatus
import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.domain.model.streamFor
import com.rumput.vision.domain.repository.AppPreferencesRepository
import com.rumput.vision.domain.repository.CameraMedia
import com.rumput.vision.domain.repository.ServerConnection
import com.rumput.vision.domain.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One grid cell. [slot] is the index of the live player showing it, or null for a still image. */
data class GridTile(
    val camera: CameraWithStatus,
    val slot: Int?,
    val stream: StreamState,
    val snapshotUrl: String?,
)

data class LiveGridUiState(
    val isLoading: Boolean = true,
    val tiles: List<GridTile> = emptyList(),
    val page: Int = 0,
    val pageCount: Int = 1,
    val connection: ConnectionState = ConnectionState.Unknown,
    val snapshotTick: Long = -1,
    val maxLiveStreams: Int = AppPreferences.DEFAULT_MAX_GRID_STREAMS,
)

/**
 * 2×2 live grid. At most [AppPreferences.maxConcurrentGridStreams] players stream at once, always
 * muted (so video only) and on the grid quality (Sub by default). Remaining cells show refreshing stills.
 * Players are created once, reassigned as pages change, and released with the ViewModel.
 */
@HiltViewModel
class LiveGridViewModel @Inject constructor(
    serverRepository: ServerRepository,
    private val connection: ServerConnection,
    private val cameraMedia: CameraMedia,
    preferences: AppPreferencesRepository,
    private val playerFactory: CameraStreamPlayerFactory,
) : ViewModel() {

    private val players = mutableListOf<CameraStreamPlayer>()
    private val playerCount = MutableStateFlow(0)
    private val page = MutableStateFlow(0)
    private var started = false

    fun player(slot: Int): Player? = players.getOrNull(slot)?.player

    @OptIn(ExperimentalCoroutinesApi::class)
    private val playerStates: Flow<List<StreamState>> = playerCount.flatMapLatest { count ->
        if (count == 0) flowOf(emptyList()) else combine(players.take(count).map { it.state }) { it.toList() }
    }

    private data class Layout(
        val cameras: List<CameraWithStatus>,
        val pageIndex: Int,
        val pageCount: Int,
        val liveCount: Int,
        val prefs: AppPreferences,
        val connection: ConnectionState,
    ) {
        val pageCameras: List<CameraWithStatus> get() = cameras.drop(pageIndex * PAGE_SIZE).take(PAGE_SIZE)
    }

    private val layout: StateFlow<Layout?> = combine(
        serverRepository.observeCameras(),
        preferences.preferences,
        page,
        connection.state,
    ) { cameras, prefs, requestedPage, conn ->
        val enabled = cameras.filter { it.status != CameraStatus.DISABLED }
        val pageCount = ((enabled.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
        val pageIndex = requestedPage.coerceIn(0, pageCount - 1)
        val pageCameras = enabled.drop(pageIndex * PAGE_SIZE).take(PAGE_SIZE)
        Layout(enabled, pageIndex, pageCount, minOf(prefs.maxConcurrentGridStreams, pageCameras.size), prefs, conn)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<LiveGridUiState> = combine(
        layout,
        playerStates,
        ticker(connection.state.map { it is ConnectionState.Connected }, SNAPSHOT_INTERVAL_MILLIS).onStart { emit(-1) },
    ) { layout, states, tick ->
        if (layout == null) return@combine LiveGridUiState()
        LiveGridUiState(
            isLoading = false,
            tiles = layout.pageCameras.mapIndexed { i, camera ->
                val slot = i.takeIf { it < layout.liveCount }
                GridTile(
                    camera = camera,
                    slot = slot,
                    stream = slot?.let { states.getOrNull(it) } ?: StreamState.Idle,
                    snapshotUrl = cameraMedia.latestFrameUrl(camera.camera.name, SNAPSHOT_HEIGHT),
                )
            },
            page = layout.pageIndex,
            pageCount = layout.pageCount,
            connection = layout.connection,
            snapshotTick = tick,
            maxLiveStreams = layout.prefs.maxConcurrentGridStreams,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LiveGridUiState())

    init {
        viewModelScope.launch { layout.collect { it?.let(::assign) } }
    }

    /** Points each live slot at its camera; players beyond the live count are stopped. */
    private fun assign(layout: Layout) {
        while (players.size < layout.liveCount) players += playerFactory.create(BufferProfile.SMOOTH)
        if (playerCount.value != players.size) playerCount.value = players.size
        players.forEachIndexed { slot, player ->
            val camera = layout.pageCameras.getOrNull(slot)?.takeIf { slot < layout.liveCount }
            val streamName = camera?.camera?.streamFor(layout.prefs.gridStreamQuality)
            val url = streamName?.let { cameraMedia.liveStreamUrl(it) }
            if (camera == null || streamName == null || url == null) {
                player.stop()
            } else {
                player.setMuted(true)
                player.prepare(StreamSource(camera.camera.name, streamName, url))
                if (started) player.play()
            }
        }
    }

    fun onStart() {
        started = true
        layout.value?.let { l -> players.take(l.liveCount).forEach { it.play() } }
    }

    fun onStop() {
        started = false
        players.forEach { it.stop() }
    }

    fun retry(slot: Int) {
        if (connection.state.value !is ConnectionState.Connected) connection.reconnect()
        players.getOrNull(slot)?.retry()
    }

    fun nextPage() = page.value.let { page.value = (it + 1).coerceAtMost((layout.value?.pageCount ?: 1) - 1) }

    fun previousPage() = page.value.let { page.value = (it - 1).coerceAtLeast(0) }

    override fun onCleared() {
        players.forEach { it.release() }
        players.clear()
    }

    companion object {
        const val PAGE_SIZE = 4
        const val SNAPSHOT_INTERVAL_MILLIS = 10_000L
        const val SNAPSHOT_HEIGHT = 360
    }
}
