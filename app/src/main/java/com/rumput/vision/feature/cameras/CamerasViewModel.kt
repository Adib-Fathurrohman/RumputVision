package com.rumput.vision.feature.cameras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rumput.vision.domain.model.CameraWithStatus
import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.domain.model.FrigateError
import com.rumput.vision.domain.model.FrigateResult
import com.rumput.vision.domain.repository.ServerConnection
import com.rumput.vision.domain.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CamerasUiState(
    val isLoading: Boolean = true,
    val cameras: List<CameraWithStatus> = emptyList(),
    val connection: ConnectionState = ConnectionState.Unknown,
    val isRefreshing: Boolean = false,
    val lastError: FrigateError? = null,
) {
    val showingCachedData: Boolean get() = cameras.isNotEmpty() && cameras.all { it.isCached }
}

@HiltViewModel
class CamerasViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val connection: ServerConnection,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val lastError = MutableStateFlow<FrigateError?>(null)

    val uiState: StateFlow<CamerasUiState> = combine(
        serverRepository.observeCameras(),
        connection.state,
        refreshing,
        lastError,
    ) { cameras, conn, isRefreshing, error ->
        CamerasUiState(isLoading = false, cameras = cameras, connection = conn, isRefreshing = isRefreshing, lastError = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CamerasUiState())

    init {
        refresh()
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
