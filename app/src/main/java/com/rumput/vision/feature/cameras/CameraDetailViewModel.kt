package com.rumput.vision.feature.cameras

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rumput.vision.core.common.ticker
import com.rumput.vision.domain.model.CameraWithStatus
import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.domain.repository.CameraMedia
import com.rumput.vision.domain.repository.ServerConnection
import com.rumput.vision.domain.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import javax.inject.Inject

data class CameraDetailUiState(
    val cameraName: String,
    val isLoading: Boolean = true,
    val camera: CameraWithStatus? = null,
    /** The camera list is known and this camera is not in it (e.g. removed from Frigate's config). */
    val notFound: Boolean = false,
    val connection: ConnectionState = ConnectionState.Unknown,
    val snapshotUrl: String? = null,
    val snapshotTick: Long = -1,
)

@HiltViewModel
class CameraDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverRepository: ServerRepository,
    private val connection: ServerConnection,
    private val cameraMedia: CameraMedia,
) : ViewModel() {

    // Navigation stores type-safe route properties under their names.
    private val cameraName: String = requireNotNull(savedStateHandle.get<String>("cameraName"))

    private val connected: Flow<Boolean> = connection.state.map { it is ConnectionState.Connected }

    /** Keeps the status figures current while this screen is observed and connected. Emits nothing. */
    private val statsRefresh: Flow<Nothing> = ticker(connected, STATS_INTERVAL_MILLIS)
        .onEach { serverRepository.refresh() }
        .transform { }

    val uiState: StateFlow<CameraDetailUiState> = merge(
        combine(
            serverRepository.observeCameras(),
            connection.state,
            ticker(connected, SNAPSHOT_INTERVAL_MILLIS).onStart { emit(-1) },
        ) { cameras, conn, tick ->
            val camera = cameras.firstOrNull { it.camera.name == cameraName }
            CameraDetailUiState(
                cameraName = cameraName,
                // An empty list means nothing is cached yet: wait for the first refresh instead of "not found".
                isLoading = camera == null && cameras.isEmpty() && conn !is ConnectionState.Unreachable,
                camera = camera,
                notFound = camera == null && cameras.isNotEmpty(),
                connection = conn,
                snapshotUrl = camera?.let { cameraMedia.latestFrameUrl(cameraName, DETAIL_HEIGHT) },
                snapshotTick = tick,
            )
        },
        statsRefresh,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CameraDetailUiState(cameraName))

    fun retry() = connection.reconnect()

    companion object {
        /** Frigate encodes each frame on request; 4 s keeps the server load low while staying useful. */
        const val SNAPSHOT_INTERVAL_MILLIS = 4_000L
        const val STATS_INTERVAL_MILLIS = 15_000L
        /** Frigate's frames come from the detect stream (1024×576 on this server); asking for more only upscales. */
        const val DETAIL_HEIGHT = 576
    }
}
