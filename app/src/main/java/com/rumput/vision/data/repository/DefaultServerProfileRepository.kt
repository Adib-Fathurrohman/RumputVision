package com.rumput.vision.data.repository

import com.rumput.vision.core.database.ServerProfileDao
import com.rumput.vision.core.security.CredentialStore
import com.rumput.vision.core.security.StoredCredentials
import com.rumput.vision.data.mapper.toDomain
import com.rumput.vision.data.mapper.toEntity
import com.rumput.vision.domain.model.ServerProfile
import com.rumput.vision.domain.repository.PasswordUpdate
import com.rumput.vision.domain.repository.ServerProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultServerProfileRepository @Inject constructor(
    private val dao: ServerProfileDao,
    private val credentialStore: CredentialStore,
) : ServerProfileRepository {

    override fun observeProfiles(): Flow<List<ServerProfile>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }.distinctUntilChanged()

    override fun observeActiveProfile(): Flow<ServerProfile?> =
        dao.observeActive().map { it?.toDomain() }.distinctUntilChanged()

    override suspend fun getProfile(id: String): ServerProfile? = dao.getById(id)?.toDomain()

    override suspend fun saveProfile(profile: ServerProfile, password: PasswordUpdate) {
        // Credentials are written first so a saved profile never points at a stale password.
        when (password) {
            PasswordUpdate.Keep -> Unit
            PasswordUpdate.Clear -> credentialStore.update(profile.id) { it?.copy(password = null) }
            PasswordUpdate.ClearAll -> credentialStore.delete(profile.id)
            is PasswordUpdate.Set -> credentialStore.update(profile.id) {
                // A new password invalidates any token issued for the previous one.
                (it ?: StoredCredentials())
                    .copy(password = password.value, accessToken = null, accessTokenExpiresAtEpochSeconds = null)
            }
        }
        dao.upsertEnsuringActive(profile.toEntity())
    }

    override suspend fun deleteProfile(id: String) {
        dao.deleteEnsuringActive(id)
        credentialStore.delete(id)
    }

    override suspend fun setActiveProfile(id: String) {
        if (dao.getById(id) != null) dao.setActive(id)
    }

    override suspend fun hasStoredPassword(id: String): Boolean = credentialStore.read(id)?.password != null
}
