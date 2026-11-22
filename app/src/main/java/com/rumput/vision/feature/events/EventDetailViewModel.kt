package com.rumput.vision.feature.events

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rumput.vision.domain.model.Event
import com.rumput.vision.domain.model.FrigateError
import com.rumput.vision.domain.model.FrigateResult
import com.rumput.vision.domain.repository.CameraMedia
import com.rumput.vision.domain.repository.EventRepository
import com.rumput.vision.domain.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EventDetailUiState(
    val isLoading: Boolean = true,
    val event: Event? = null,
    val cameraName: String? = null,
    val snapshotUrl: String? = null,
    val error: FrigateError? = null,
)

/** Recording window for an event: a little context either side, like Frigate's own clip padding. */
data class ClipWindow(val camera: String, val startEpochSeconds: Long, val endEpochSeconds: Long)

@HiltViewModel
class EventDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val events: EventRepository,
    serverRepository: ServerRepository,
    private val media: CameraMedia,
) : ViewModel() {
    private val eventId: String = requireNotNull(savedStateHandle.get<String>("eventId"))
    private val loaded = MutableStateFlow<FrigateResult<Event>?>(null)

    val uiState: StateFlow<EventDetailUiState> = combine(loaded, serverRepository.observeCameras()) { result, cameras ->
        when (result) {
            null -> EventDetailUiState()
            is FrigateResult.Failure -> EventDetailUiState(isLoading = false, error = result.error)
            is FrigateResult.Success -> EventDetailUiState(
                isLoading = false,
                event = result.value,
                cameraName = cameras.firstOrNull { it.camera.name == result.value.camera }?.camera?.displayName,
                snapshotUrl = if (result.value.hasSnapshot) media.eventSnapshotUrl(result.value.id, SNAPSHOT_HEIGHT) else null,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventDetailUiState())

    init {
        load()
    }

    fun load() {
        viewModelScope.launch { loaded.value = events.getEvent(eventId) }
    }

    fun clipWindow(event: Event, nowMillis: Long = System.currentTimeMillis()): ClipWindow = ClipWindow(
        camera = event.camera,
        startEpochSeconds = event.startEpochMillis / 1000 - CLIP_PADDING_SECONDS,
        endEpochSeconds = (event.endEpochMillis ?: nowMillis) / 1000 + CLIP_PADDING_SECONDS,
    )

    companion object {
        const val SNAPSHOT_HEIGHT = 576
        const val CLIP_PADDING_SECONDS = 5L
    }
}
