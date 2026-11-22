package com.rumput.vision.domain.repository

import com.rumput.vision.domain.model.ServerProfile
import kotlinx.coroutines.flow.Flow

interface ServerProfileRepository {
    fun observeProfiles(): Flow<List<ServerProfile>>

    fun observeActiveProfile(): Flow<ServerProfile?>

    suspend fun getProfile(id: String): ServerProfile?

    /**
     * Inserts or updates [profile]. The first saved profile becomes active automatically.
     * [password] controls the stored password for this profile.
     */
    suspend fun saveProfile(profile: ServerProfile, password: PasswordUpdate)

    /** Deletes the profile and its credentials. Another profile becomes active if needed. */
    suspend fun deleteProfile(id: String)

    suspend fun setActiveProfile(id: String)

    suspend fun hasStoredPassword(id: String): Boolean
}

sealed interface PasswordUpdate {
    data object Keep : PasswordUpdate

    /** Forget the password but keep a still-valid session token. */
    data object Clear : PasswordUpdate

    /** Forget password and token, e.g. when the username or auth mode changes. */
    data object ClearAll : PasswordUpdate

    class Set(val value: String) : PasswordUpdate {
        override fun toString(): String = "Set(***)"
    }
}
