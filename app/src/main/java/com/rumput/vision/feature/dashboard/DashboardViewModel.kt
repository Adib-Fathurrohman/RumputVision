package com.rumput.vision.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.domain.model.ServerProfile
import com.rumput.vision.domain.repository.ServerProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = true,
    val activeProfile: ServerProfile? = null,
    val connection: ConnectionState = ConnectionState.Unknown,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    profileRepository: ServerProfileRepository,
) : ViewModel() {
    val uiState: StateFlow<DashboardUiState> = profileRepository.observeActiveProfile()
        .map { profile ->
            DashboardUiState(
                isLoading = false,
                activeProfile = profile,
                // Server connection is implemented in Phase 2; until then the state is honestly "unknown".
                connection = if (profile == null) ConnectionState.NotConfigured else ConnectionState.Unknown,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())
}
