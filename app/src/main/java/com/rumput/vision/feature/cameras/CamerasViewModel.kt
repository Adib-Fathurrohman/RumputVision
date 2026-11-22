package com.rumput.vision.feature.cameras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rumput.vision.domain.model.CameraWithStatus
import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.domain.model.FrigateError
import com.rumput.vision.domain.model.FrigateResult
import com.rumput.vision.core.common.ticker
import com.rumput.vision.domain.repository.CameraMedia
import com.rumput.vision.domain.repository.ServerConnection
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

data class CamerasUiState(
    val isLoading: Boolean = true,
    val cameras: List<CameraWithStatus> = emptyList(),
    val connection: ConnectionState = ConnectionState.Unknown,
    val isRefreshing: Boolean = false,
    val lastError: FrigateError? = null,
    val snapshotTick: Long = -1,
    val snapshotUrls: Map<String, String?> = emptyMap(),
) {
    val showingCachedData: Boolean get() = cameras.isNotEmpty() && cameras.all { it.isCached }
}

@HiltViewModel
class CamerasViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val connection: ServerConnection,
    private val cameraMedia: CameraMedia,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val lastError = MutableStateFlow<FrigateError?>(null)

    private val thumbnailTicks =
        ticker(connection.state.map { it is ConnectionState.Connected }, THUMBNAIL_INTERVAL_MILLIS).onStart { emit(-1) }

    val uiState: StateFlow<CamerasUiState> = combine(
        serverRepository.observeCameras(),
        connection.state,
        refreshing,
        lastError,
        thumbnailTicks,
    ) { cameras, conn, isRefreshing, error, tick ->
        CamerasUiState(
            isLoading = false,
            cameras = cameras,
            connection = conn,
            isRefreshing = isRefreshing,
            lastError = error,
            snapshotTick = tick,
            snapshotUrls = cameras.associate { it.camera.name to cameraMedia.latestFrameUrl(it.camera.name, THUMBNAIL_HEIGHT) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CamerasUiState())

    init {
        refresh()
    }

    private companion object {
        const val THUMBNAIL_INTERVAL_MILLIS = 30_000L
        const val THUMBNAIL_HEIGHT = 180
    }

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            lastError.value = (serverRepository.refresh() as? FrigateResult.Failure)?.error
            refreshing.value = false
        }
    }
}
