package com.rumput.vision.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rumput.vision.domain.model.AuthMode
import com.rumput.vision.domain.model.EndpointPreference
import com.rumput.vision.domain.repository.ServerProfileRepository
import com.rumput.vision.domain.usecase.ProfileValidation
import com.rumput.vision.domain.usecase.SaveProfileResult
import com.rumput.vision.domain.usecase.SaveServerProfileUseCase
import com.rumput.vision.domain.usecase.ServerProfileDraft
import com.rumput.vision.domain.usecase.ValidateServerProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerProfileEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val draft: ServerProfileDraft = ServerProfileDraft(),
    val hasStoredPassword: Boolean = false,
    val validation: ProfileValidation = ProfileValidation(),
    /** Errors are shown only after the first save attempt; warnings are always shown. */
    val showErrors: Boolean = false,
    val isSaving: Boolean = false,
    val notFound: Boolean = false,
    val finished: Boolean = false,
)

@HiltViewModel
class ServerProfileEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ServerProfileRepository,
    private val validate: ValidateServerProfileUseCase,
    private val saveProfile: SaveServerProfileUseCase,
) : ViewModel() {

    // Navigation stores type-safe route properties under their names.
    private val profileId: String? = savedStateHandle.get<String>("profileId")

    private val _uiState = MutableStateFlow(ServerProfileEditUiState(isNew = profileId == null, isLoading = profileId != null))
    val uiState: StateFlow<ServerProfileEditUiState> = _uiState.asStateFlow()

    init {
        if (profileId == null) {
            revalidate()
        } else {
            viewModelScope.launch { load(profileId) }
        }
    }

    private suspend fun load(id: String) {
        val profile = repository.getProfile(id)
        if (profile == null) {
            _uiState.update { it.copy(isLoading = false, notFound = true) }
            return
        }
        val hasPassword = repository.hasStoredPassword(id)
        _uiState.update {
            it.copy(
                isLoading = false,
                draft = ServerProfileDraft(
                    id = profile.id,
                    displayName = profile.displayName,
                    localUrl = profile.localUrl?.value.orEmpty(),
                    remoteUrl = profile.remoteUrl?.value.orEmpty(),
                    authMode = profile.authMode,
                    username = profile.username.orEmpty(),
                    rememberPassword = hasPassword || profile.authMode == AuthMode.NONE,
                    endpointPreference = profile.endpointPreference,
                ),
                hasStoredPassword = hasPassword,
            )
        }
        revalidate()
    }

    fun onDisplayNameChange(value: String) = edit { copy(displayName = value) }

    fun onLocalUrlChange(value: String) = edit { copy(localUrl = value) }

    fun onRemoteUrlChange(value: String) = edit { copy(remoteUrl = value) }

    fun onAuthModeChange(value: AuthMode) = edit { copy(authMode = value) }

    fun onUsernameChange(value: String) = edit { copy(username = value) }

    fun onPasswordChange(value: String) = edit { copy(password = value) }

    fun onRememberPasswordChange(value: Boolean) = edit { copy(rememberPassword = value) }

    fun onEndpointPreferenceChange(value: EndpointPreference) = edit { copy(endpointPreference = value) }

    fun save() {
        val current = _uiState.value
        if (current.isSaving || current.isLoading) return
        _uiState.update { it.copy(isSaving = true, showErrors = true) }
        viewModelScope.launch {
            when (val result = saveProfile(_uiState.value.draft)) {
                is SaveProfileResult.Saved -> _uiState.update {
                    // Drop the password from memory as soon as it is persisted.
                    it.copy(isSaving = false, finished = true, draft = it.draft.copy(password = ""))
                }
                is SaveProfileResult.Invalid -> _uiState.update { it.copy(isSaving = false, validation = result.validation) }
            }
        }
    }

    fun delete() {
        val id = profileId ?: return
        viewModelScope.launch {
            repository.deleteProfile(id)
            _uiState.update { it.copy(finished = true, draft = it.draft.copy(password = "")) }
        }
    }

    private fun edit(transform: ServerProfileDraft.() -> ServerProfileDraft) {
        _uiState.update { it.copy(draft = it.draft.transform()) }
        revalidate()
    }

    private fun revalidate() {
        _uiState.update { it.copy(validation = validate(it.draft)) }
    }
}
