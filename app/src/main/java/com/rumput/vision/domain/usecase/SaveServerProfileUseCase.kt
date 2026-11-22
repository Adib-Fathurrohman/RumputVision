package com.rumput.vision.domain.usecase

import com.rumput.vision.core.common.Clock
import com.rumput.vision.core.common.IdGenerator
import com.rumput.vision.domain.model.AuthMode
import com.rumput.vision.domain.model.EndpointPreference
import com.rumput.vision.domain.model.ServerProfile
import com.rumput.vision.domain.model.ServerUrl
import com.rumput.vision.domain.repository.PasswordUpdate
import com.rumput.vision.domain.repository.ServerProfileRepository
import javax.inject.Inject

/** Raw user input for creating or editing a server profile. */
data class ServerProfileDraft(
    val id: String? = null,
    val displayName: String = "",
    val localUrl: String = "",
    val remoteUrl: String = "",
    val authMode: AuthMode = AuthMode.FRIGATE_LOGIN,
    val username: String = "",
    /** Blank means "keep the stored password" when editing. */
    val password: String = "",
    val rememberPassword: Boolean = true,
    val endpointPreference: EndpointPreference = EndpointPreference.AUTOMATIC,
) {
    override fun toString(): String =
        "ServerProfileDraft(id=$id, displayName=$displayName, localUrl=$localUrl, remoteUrl=$remoteUrl, " +
            "authMode=$authMode, username=$username, password=***, rememberPassword=$rememberPassword, " +
            "endpointPreference=$endpointPreference)"
}

data class ProfileValidation(
    val nameError: NameError? = null,
    val localUrlError: UrlError? = null,
    val remoteUrlError: UrlError? = null,
    val usernameRequired: Boolean = false,
    val formError: FormError? = null,
    val localUrlWarnings: Set<UrlWarning> = emptySet(),
    val remoteUrlWarnings: Set<UrlWarning> = emptySet(),
) {
    val isValid: Boolean
        get() = nameError == null && localUrlError == null && remoteUrlError == null &&
            !usernameRequired && formError == null
}

enum class NameError { REQUIRED, TOO_LONG }

enum class FormError {
    AT_LEAST_ONE_URL,
    LOCAL_URL_REQUIRED_FOR_PREFERENCE,
    REMOTE_URL_REQUIRED_FOR_PREFERENCE,
}

sealed interface SaveProfileResult {
    data class Saved(val profileId: String) : SaveProfileResult
    data class Invalid(val validation: ProfileValidation) : SaveProfileResult
}

class ValidateServerProfileUseCase @Inject constructor() {

    operator fun invoke(draft: ServerProfileDraft): ProfileValidation = validate(draft).validation

    internal data class Outcome(val validation: ProfileValidation, val local: ServerUrl?, val remote: ServerUrl?)

    internal fun validate(draft: ServerProfileDraft): Outcome {
        val name = draft.displayName.trim()
        val nameError = when {
            name.isEmpty() -> NameError.REQUIRED
            name.length > MAX_NAME_LENGTH -> NameError.TOO_LONG
            else -> null
        }
        val local = parseOptional(draft.localUrl, draft.authMode, UrlRole.LOCAL)
        val remote = parseOptional(draft.remoteUrl, draft.authMode, UrlRole.REMOTE)

        val formError = when {
            draft.localUrl.isBlank() && draft.remoteUrl.isBlank() -> FormError.AT_LEAST_ONE_URL
            draft.endpointPreference == EndpointPreference.LOCAL_ONLY && draft.localUrl.isBlank() ->
                FormError.LOCAL_URL_REQUIRED_FOR_PREFERENCE
            draft.endpointPreference == EndpointPreference.REMOTE_ONLY && draft.remoteUrl.isBlank() ->
                FormError.REMOTE_URL_REQUIRED_FOR_PREFERENCE
            else -> null
        }
        val validation = ProfileValidation(
            nameError = nameError,
            localUrlError = local.error,
            remoteUrlError = remote.error,
            usernameRequired = draft.authMode == AuthMode.FRIGATE_LOGIN && draft.username.isBlank(),
            formError = formError,
            localUrlWarnings = local.warnings,
            remoteUrlWarnings = remote.warnings,
        )
        return Outcome(validation, local.url, remote.url)
    }

    private data class Field(val url: ServerUrl?, val error: UrlError?, val warnings: Set<UrlWarning>)

    private fun parseOptional(raw: String, authMode: AuthMode, role: UrlRole): Field {
        if (raw.isBlank()) return Field(null, null, emptySet())
        return when (val result = ServerUrlPolicy.parse(raw)) {
            is ServerUrlPolicy.ParseResult.Invalid -> Field(null, result.error, emptySet())
            is ServerUrlPolicy.ParseResult.Valid -> {
                val policyError = ServerUrlPolicy.policyError(result.url, authMode)
                Field(
                    url = result.url.takeIf { policyError == null },
                    error = policyError,
                    warnings = ServerUrlPolicy.warnings(result.url, role),
                )
            }
        }
    }

    companion object {
        const val MAX_NAME_LENGTH = 60
    }
}

class SaveServerProfileUseCase @Inject constructor(
    private val repository: ServerProfileRepository,
    private val validator: ValidateServerProfileUseCase,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(draft: ServerProfileDraft): SaveProfileResult {
        val outcome = validator.validate(draft)
        if (!outcome.validation.isValid) return SaveProfileResult.Invalid(outcome.validation)

        val existing = draft.id?.let { repository.getProfile(it) }
        val now = clock.nowEpochMillis()
        val profile = ServerProfile(
            id = existing?.id ?: idGenerator.newId(),
            displayName = draft.displayName.trim(),
            localUrl = outcome.local,
            remoteUrl = outcome.remote,
            authMode = draft.authMode,
            username = draft.username.trim().takeIf { draft.authMode == AuthMode.FRIGATE_LOGIN && it.isNotEmpty() },
            endpointPreference = draft.endpointPreference,
            isActive = existing?.isActive ?: false,
            createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = now,
        )
        val usernameChanged = existing != null && existing.username != profile.username
        val passwordUpdate = when {
            draft.authMode == AuthMode.NONE -> PasswordUpdate.ClearAll
            // Stored secrets belong to the old username; never reuse them for a new one.
            usernameChanged && (!draft.rememberPassword || draft.password.isEmpty()) -> PasswordUpdate.ClearAll
            !draft.rememberPassword -> PasswordUpdate.Clear
            draft.password.isNotEmpty() -> PasswordUpdate.Set(draft.password)
            else -> PasswordUpdate.Keep
        }
        repository.saveProfile(profile, passwordUpdate)
        return SaveProfileResult.Saved(profile.id)
    }
}
