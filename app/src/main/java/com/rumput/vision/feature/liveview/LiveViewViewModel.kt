package com.rumput.vision.feature.liveview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.rumput.vision.core.streaming.BufferProfile
import com.rumput.vision.core.streaming.CameraStreamPlayer
import com.rumput.vision.core.streaming.CameraStreamPlayerFactory
import com.rumput.vision.core.streaming.StreamSource
import com.rumput.vision.core.streaming.StreamState
import com.rumput.vision.domain.model.CameraStatus
import com.rumput.vision.domain.model.CameraWithStatus
import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.domain.model.StreamQuality
import com.rumput.vision.domain.model.hasQualityChoice
import com.rumput.vision.domain.model.streamFor
import com.rumput.vision.domain.repository.AppPreferencesRepository
import com.rumput.vision.domain.repository.CameraMedia
import com.rumput.vision.domain.repository.ServerConnection
import com.rumput.vision.domain.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LiveViewUiState(
    val cameraName: String,
    val camera: CameraWithStatus? = null,
    /** Enabled cameras, for switching. */
    val cameras: List<CameraWithStatus> = emptyList(),
    val quality: StreamQuality = StreamQuality.MAIN,
    val muted: Boolean = true,
    val stream: StreamState = StreamState.Idle,
    val connection: ConnectionState = ConnectionState.Unknown,
    /** The go2rtc stream being played, for display. */
    val streamName: String? = null,
    val rebufferingFrequently: Boolean = false,
) {
    /** Suggest the lighter stream when the main one keeps stalling. */
    val suggestSubStream: Boolean
        get() = rebufferingFrequently && quality == StreamQuality.MAIN && canSwitchQuality

    val canSwitchQuality: Boolean get() = camera?.camera?.hasQualityChoice == true
    val canSwitchCamera: Boolean get() = cameras.size > 1
}

@HiltViewModel
class LiveViewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    serverRepository: ServerRepository,
    private val connection: ServerConnection,
    private val cameraMedia: CameraMedia,
    preferences: AppPreferencesRepository,
    playerFactory: CameraStreamPlayerFactory,
) : ViewModel() {

    /** Created once per screen and released in [onCleared]: recomposition never creates players. */
    private val streamPlayer: CameraStreamPlayer = playerFactory.create(BufferProfile.LOW_LATENCY)
    val player: Player? get() = streamPlayer.player

    private val selection = MutableStateFlow(
        Selection(cameraName = requireNotNull(savedStateHandle.get<String>("cameraName")), quality = null, muted = null),
    )

    private data class Selection(val cameraName: String, val quality: StreamQuality?, val muted: Boolean?)

    val uiState: StateFlow<LiveViewUiState> = combine(
        combine(serverRepository.observeCameras(), connection.state, ::Pair),
        preferences.preferences,
        selection,
        streamPlayer.state,
        streamPlayer.rebufferingFrequently,
    ) { (cameras, conn), prefs, sel, stream, struggling ->
        val enabled = cameras.filter { it.status != CameraStatus.DISABLED }
        val camera = cameras.firstOrNull { it.camera.name == sel.cameraName }
        val quality = sel.quality ?: prefs.fullscreenStreamQuality
        LiveViewUiState(
            cameraName = sel.cameraName,
            camera = camera,
            cameras = enabled,
            quality = quality,
            muted = sel.muted ?: prefs.startMuted,
            stream = stream,
            connection = conn,
            streamName = camera?.camera?.streamFor(quality),
            rebufferingFrequently = struggling,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LiveViewUiState(selection.value.cameraName))

    init {
        // Keep the player's source and mute state in step with the selection and the connection.
        viewModelScope.launch {
            uiState.collect { state ->
                streamPlayer.setMuted(state.muted)
                val streamName = state.streamName ?: return@collect
                val url = cameraMedia.liveStreamUrl(streamName) ?: return@collect
                streamPlayer.prepare(StreamSource(state.cameraName, streamName, url))
            }
        }
        // After the server comes back, retry a stream that failed while it was away.
        viewModelScope.launch {
            var wasConnected = connection.state.first() is ConnectionState.Connected
            connection.state.collect { state ->
                val connected = state is ConnectionState.Connected
                if (connected && !wasConnected && streamPlayer.state.value is StreamState.Error) streamPlayer.retry()
                wasConnected = connected
            }
        }
    }

    fun onStart() = streamPlayer.play()

    fun onStop() = streamPlayer.stop()

    fun retry() {
        if (connection.state.value !is ConnectionState.Connected) connection.reconnect()
        streamPlayer.retry()
    }

    fun setQuality(quality: StreamQuality) = selection.value.let { selection.value = it.copy(quality = quality) }

    fun toggleMute() {
        val muted = uiState.value.muted
        selection.value = selection.value.copy(muted = !muted)
    }

    /** Moves to the next (+1) or previous (-1) enabled camera, keeping quality and mute choices. */
    fun switchCamera(delta: Int) {
        val cameras = uiState.value.cameras
        if (cameras.size < 2) return
        val index = cameras.indexOfFirst { it.camera.name == selection.value.cameraName }.coerceAtLeast(0)
        val next = cameras[Math.floorMod(index + delta, cameras.size)]
        selection.value = selection.value.copy(cameraName = next.camera.name)
    }

    override fun onCleared() {
        streamPlayer.release()
    }
}
