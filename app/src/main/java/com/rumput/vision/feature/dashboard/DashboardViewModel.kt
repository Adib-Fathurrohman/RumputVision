package com.rumput.vision.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rumput.vision.domain.model.CameraStatus
import com.rumput.vision.domain.model.CameraWithStatus
import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.domain.model.ServerProfile
import com.rumput.vision.domain.model.ServerStatus
import com.rumput.vision.core.common.ticker
import com.rumput.vision.domain.model.Event
import com.rumput.vision.domain.model.EventFilter
import com.rumput.vision.domain.repository.CameraMedia
import com.rumput.vision.domain.repository.EventRepository
import com.rumput.vision.domain.repository.ServerConnection
import com.rumput.vision.domain.repository.ServerProfileRepository
import com.rumput.vision.domain.repository.ServerRepository
import com.rumput.vision.domain.repository.SignInResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignInUiState(
    val inProgress: Boolean = false,
    val result: SignInResult? = null,
)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val activeProfile: ServerProfile? = null,
    val connection: ConnectionState = ConnectionState.Unknown,
    val serverStatus: ServerStatus? = null,
    val cameras: List<CameraWithStatus> = emptyList(),
    val signIn: SignInUiState = SignInUiState(),
    /** Changes every [SNAPSHOT_INTERVAL_MILLIS] while connected; previews reload when it changes. */
    val snapshotTick: Long = -1,
    val snapshotUrls: Map<String, String?> = emptyMap(),
    val recentEvents: List<Event> = emptyList(),
) {
    val dashboardCameras: List<CameraWithStatus> get() = cameras.filter { it.camera.showOnDashboard }

    val onlineCount: Int get() = cameras.count { it.status == CameraStatus.ONLINE }
    val showingCachedData: Boolean get() = cameras.isNotEmpty() && cameras.all { it.isCached }
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    profileRepository: ServerProfileRepository,
    private val connection: ServerConnection,
    private val serverRepository: ServerRepository,
    private val cameraMedia: CameraMedia,
    private val events: EventRepository,
) : ViewModel() {

    fun eventThumbnailUrl(event: Event): String? = if (event.hasSnapshot) cameraMedia.eventThumbnailUrl(event.id) else null

    private val signIn = MutableStateFlow(SignInUiState())

    /**
     * Refreshes while the dashboard is collected and the server is connected, then stops.
     * Emits nothing; merged into [uiState] so it follows the screen's lifecycle.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val polling: Flow<Nothing> = connection.state
        .map { it is ConnectionState.Connected }
        .distinctUntilChanged()
        .transformLatest { connected ->
            while (connected) {
                serverRepository.refresh()
                events.fetchEvents(EventFilter(), beforeEpochMillis = null, limit = RECENT_EVENTS)
                delay(REFRESH_INTERVAL_MILLIS)
            }
        }

    /** New events announced over the realtime socket refresh the list right away. */
    private val realtimeEvents: Flow<Nothing> = events.eventActivity
        .onEach { events.fetchEvents(EventFilter(), beforeEpochMillis = null, limit = RECENT_EVENTS) }
        .transform { }

    private val snapshotTicks: Flow<Long> =
        ticker(connection.state.map { it is ConnectionState.Connected }, SNAPSHOT_INTERVAL_MILLIS).onStart { emit(-1) }

    val uiState: StateFlow<DashboardUiState> = merge(
        combine(
            combine(
                profileRepository.observeActiveProfile(),
                connection.state,
                serverRepository.observeServerStatus(),
                serverRepository.observeCameras(),
                signIn,
            ) { profile, conn, status, cameras, signInState ->
                DashboardUiState(
                    isLoading = false,
                    activeProfile = profile,
                    connection = conn,
                    serverStatus = status,
                    cameras = cameras,
                    signIn = signInState,
                )
            },
            snapshotTicks,
            events.observeCachedEvents(EventFilter(), RECENT_EVENTS),
        ) { state, tick, recent ->
            state.copy(
                snapshotTick = tick,
                snapshotUrls = state.cameras.associate { it.camera.name to cameraMedia.latestFrameUrl(it.camera.name, PREVIEW_HEIGHT) },
                recentEvents = recent,
            )
        },
        polling,
        realtimeEvents,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun retry() = connection.reconnect()

    fun signIn(password: String, remember: Boolean) {
        if (signIn.value.inProgress || password.isEmpty()) return
        viewModelScope.launch {
            signIn.value = SignInUiState(inProgress = true)
            val result = connection.signIn(password, remember)
            signIn.update { SignInUiState(inProgress = false, result = result) }
        }
    }

    companion object {
        private const val REFRESH_INTERVAL_MILLIS = 30_000L

        /** Frigate encodes each frame on request (~0.4–1.2 s on this server), so keep this well above that. */
        const val SNAPSHOT_INTERVAL_MILLIS = 10_000L
        const val PREVIEW_HEIGHT = 360
        const val RECENT_EVENTS = 5
    }
}
