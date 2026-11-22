package com.rumput.vision.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rumput.vision.domain.repository.ServerProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface AppStartState {
    data object Loading : AppStartState
    data class Ready(val hasProfiles: Boolean) : AppStartState
}

@HiltViewModel
class AppViewModel @Inject constructor(
    profileRepository: ServerProfileRepository,
) : ViewModel() {
    val startState: StateFlow<AppStartState> = profileRepository.observeProfiles()
        .map { AppStartState.Ready(hasProfiles = it.isNotEmpty()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppStartState.Loading)
}
