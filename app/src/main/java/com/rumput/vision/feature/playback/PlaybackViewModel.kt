package com.rumput.vision.feature.playback

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.rumput.vision.core.common.ticker
import com.rumput.vision.core.streaming.PlaybackState
import com.rumput.vision.core.streaming.RecordingPlayer
import com.rumput.vision.core.streaming.RecordingPlayerFactory
import com.rumput.vision.core.streaming.RecordingTimeline
import com.rumput.vision.domain.model.FrigateResult
import com.rumput.vision.domain.repository.CameraMedia
import com.rumput.vision.domain.repository.RecordingRepository
import com.rumput.vision.domain.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaybackUiState(
    val camera: String,
    val cameraName: String? = null,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val playback: PlaybackState = PlaybackState.Idle,
    val positionMillis: Long = 0,
    /** Wall-clock time of the current frame, when the segment layout is known. */
    val wallClockMillis: Long? = null,
    val timeline: RecordingTimeline? = null,
    val aspectRatio: Float = 16f / 9f,
)

@HiltViewModel
class PlaybackViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    playerFactory: RecordingPlayerFactory,
    recordings: RecordingRepository,
    serverRepository: ServerRepository,
    media: CameraMedia,
) : ViewModel() {
    private val camera: String = requireNotNull(savedStateHandle.get<String>("camera"))
    private val startSeconds: Long = requireNotNull(savedStateHandle.get<Long>("startEpochSeconds"))
    private val endSeconds: Long = requireNotNull(savedStateHandle.get<Long>("endEpochSeconds"))

    private val recordingPlayer: RecordingPlayer = playerFactory.create()
    val player: Player? get() = recordingPlayer.player

    private val segments = MutableStateFlow<List<com.rumput.vision.domain.model.RecordingSegment>?>(null)

    /** Position polling only while something is playing and the screen observes this state. */
    private val positions = ticker(recordingPlayer.state.map { it is PlaybackState.Ready && it.playing }, POSITION_INTERVAL_MILLIS)
        .map { recordingPlayer.positionMillis }
        .onStart { emit(0) }

    val uiState: StateFlow<PlaybackUiState> = combine(
        recordingPlayer.state,
        positions,
        segments,
        serverRepository.observeCameras(),
    ) { playback, _, segs, cameras ->
        val position = recordingPlayer.positionMillis
        // Anchored on the player's real duration once known; see RecordingTimeline.
        val line = segs?.let {
            RecordingTimeline(it, startSeconds * 1000, endSeconds * 1000, (playback as? PlaybackState.Ready)?.durationMillis)
        }
        val cam = cameras.firstOrNull { it.camera.name == camera }?.camera
        PlaybackUiState(
            camera = camera,
            cameraName = cam?.displayName,
            startEpochMillis = startSeconds * 1000,
            endEpochMillis = endSeconds * 1000,
            playback = playback,
            positionMillis = position,
            wallClockMillis = line?.wallClockAt(position),
            timeline = line,
            aspectRatio = cam?.let { c ->
                val w = c.detectWidth
                val h = c.detectHeight
                if (w != null && h != null && h > 0) (w.toFloat() / h).coerceIn(0.5f, 3f) else null
            } ?: (16f / 9f),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaybackUiState(camera, null, startSeconds * 1000, endSeconds * 1000))

    init {
        media.recordingPlaylistUrl(camera, startSeconds, endSeconds)?.let(recordingPlayer::load)
        viewModelScope.launch {
            val result = recordings.segments(camera, startSeconds * 1000, endSeconds * 1000)
            if (result is FrigateResult.Success) segments.value = result.value
        }
    }

    fun togglePlay() {
        val state = recordingPlayer.state.value
        if (state is PlaybackState.Ready && state.playing) recordingPlayer.pause() else recordingPlayer.play()
    }

    fun seekTo(positionMillis: Long) = recordingPlayer.seekTo(positionMillis)

    fun seekBy(deltaMillis: Long) = recordingPlayer.seekTo(recordingPlayer.positionMillis + deltaMillis)

    /** Pause when the screen stops; playback resumes only on the user's command. */
    fun onStop() = recordingPlayer.pause()

    override fun onCleared() = recordingPlayer.release()

    private companion object {
        const val POSITION_INTERVAL_MILLIS = 250L
    }
}
