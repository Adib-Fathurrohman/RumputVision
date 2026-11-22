package com.rumput.vision.feature.recordings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rumput.vision.domain.model.CameraStatus
import com.rumput.vision.domain.model.CameraWithStatus
import com.rumput.vision.domain.model.FrigateError
import com.rumput.vision.domain.model.FrigateResult
import com.rumput.vision.domain.model.RecordingDay
import com.rumput.vision.domain.repository.RecordingRepository
import com.rumput.vision.domain.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class RecordingsUiState(
    val cameras: List<CameraWithStatus> = emptyList(),
    val selectedCamera: String? = null,
    val days: List<RecordingDay> = emptyList(),
    val selectedDay: String? = null,
    val isLoading: Boolean = true,
    val error: FrigateError? = null,
) {
    val day: RecordingDay? get() = days.firstOrNull { it.date == selectedDay }
}

/** A playable window of recordings. */
data class PlaybackWindow(val camera: String, val startEpochSeconds: Long, val endEpochSeconds: Long)

@HiltViewModel
class RecordingsViewModel @Inject constructor(
    serverRepository: ServerRepository,
    private val recordings: RecordingRepository,
) : ViewModel() {

    private data class Loaded(val camera: String?, val days: List<RecordingDay>, val day: String?, val loading: Boolean, val error: FrigateError?)

    private val loaded = MutableStateFlow(Loaded(null, emptyList(), null, loading = true, error = null))
    private val cameras = serverRepository.observeCameras()
    private var job: Job? = null

    val uiState: StateFlow<RecordingsUiState> = combine(cameras, loaded) { cams, l ->
        RecordingsUiState(
            cameras = cams.filter { it.camera.recordingEnabled || it.status != CameraStatus.DISABLED },
            selectedCamera = l.camera,
            days = l.days,
            selectedDay = l.day,
            isLoading = l.loading,
            error = l.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordingsUiState())

    init {
        viewModelScope.launch {
            val first = cameras.first { it.isNotEmpty() }.first().camera.name
            if (loaded.value.camera == null) selectCamera(first)
        }
    }

    fun selectCamera(camera: String) {
        job?.cancel()
        loaded.value = Loaded(camera, emptyList(), null, loading = true, error = null)
        job = viewModelScope.launch {
            loaded.value = when (val result = recordings.recordingDays(camera)) {
                is FrigateResult.Success -> Loaded(camera, result.value, result.value.firstOrNull()?.date, false, null)
                is FrigateResult.Failure -> Loaded(camera, emptyList(), null, false, result.error)
            }
        }
    }

    fun selectDay(date: String) {
        loaded.value = loaded.value.copy(day = date)
    }

    fun retry() = loaded.value.camera?.let(::selectCamera)

    /** One local hour of the selected day. */
    fun hourWindow(date: String, hour: Int, zone: ZoneId = ZoneId.systemDefault()): PlaybackWindow? {
        val camera = loaded.value.camera ?: return null
        val start = LocalDate.parse(date).atStartOfDay(zone).plusHours(hour.toLong()).toEpochSecond()
        return PlaybackWindow(camera, start, start + 3600)
    }

    /** The whole selected day. */
    fun dayWindow(date: String, zone: ZoneId = ZoneId.systemDefault()): PlaybackWindow? {
        val camera = loaded.value.camera ?: return null
        val day = LocalDate.parse(date)
        return PlaybackWindow(camera, day.atStartOfDay(zone).toEpochSecond(), day.plusDays(1).atStartOfDay(zone).toEpochSecond())
    }
}
