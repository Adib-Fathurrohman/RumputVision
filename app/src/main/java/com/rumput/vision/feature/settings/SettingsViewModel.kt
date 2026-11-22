package com.rumput.vision.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rumput.vision.domain.model.AppPreferences
import com.rumput.vision.domain.model.ServerProfile
import com.rumput.vision.domain.model.StreamQuality
import com.rumput.vision.domain.repository.AppPreferencesRepository
import com.rumput.vision.domain.repository.ServerProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isLoading: Boolean = true,
    val profiles: List<ServerProfile> = emptyList(),
    val preferences: AppPreferences = AppPreferences(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profileRepository: ServerProfileRepository,
    private val preferencesRepository: AppPreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        profileRepository.observeProfiles(),
        preferencesRepository.preferences,
    ) { profiles, prefs -> SettingsUiState(isLoading = false, profiles = profiles, preferences = prefs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setActiveProfile(id: String) = launch { profileRepository.setActiveProfile(id) }

    fun setGridQuality(quality: StreamQuality) = launch { preferencesRepository.setGridStreamQuality(quality) }

    fun setFullscreenQuality(quality: StreamQuality) = launch { preferencesRepository.setFullscreenStreamQuality(quality) }

    fun setMaxGridStreams(count: Int) = launch { preferencesRepository.setMaxConcurrentGridStreams(count) }

    fun setStartMuted(muted: Boolean) = launch { preferencesRepository.setStartMuted(muted) }

    fun setInAppEventUpdates(enabled: Boolean) = launch { preferencesRepository.setInAppEventUpdates(enabled) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
