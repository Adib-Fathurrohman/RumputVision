package com.rumput.vision.core.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores one encrypted JSON blob per profile in a dedicated DataStore file.
 * The profile ID is the GCM associated data, so blobs cannot be swapped between profiles.
 * Unreadable blobs (e.g. after the Keystore key was invalidated) are deleted: the user signs in again.
 */
@Singleton
class EncryptedCredentialStore @Inject constructor(
    @param:CredentialsDataStore private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
) : CredentialStore {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun read(profileId: String): StoredCredentials? {
        val key = keyFor(profileId)
        val encoded = dataStore.data.first()[key] ?: return null
        val decoded = decode(profileId, encoded)
        if (decoded == null) dataStore.edit { it.remove(key) }
        return decoded
    }

    override suspend fun update(profileId: String, transform: (StoredCredentials?) -> StoredCredentials?) {
        val key = keyFor(profileId)
        dataStore.edit { prefs ->
            val current = prefs[key]?.let { decode(profileId, it) }
            val next = transform(current)
            if (next == null || next.isEmpty) {
                prefs.remove(key)
            } else {
                prefs[key] = encode(profileId, next)
            }
        }
    }

    override suspend fun delete(profileId: String) {
        dataStore.edit { it.remove(keyFor(profileId)) }
    }

    private fun encode(profileId: String, credentials: StoredCredentials): String {
        val plaintext = json.encodeToString(StoredCredentials.serializer(), credentials).toByteArray(Charsets.UTF_8)
        return Base64.getEncoder().encodeToString(cipher.encrypt(plaintext, profileId.toByteArray(Charsets.UTF_8)))
    }

    private fun decode(profileId: String, encoded: String): StoredCredentials? = try {
        val bytes = Base64.getDecoder().decode(encoded)
        val plaintext = cipher.decrypt(bytes, profileId.toByteArray(Charsets.UTF_8))
        json.decodeFromString(StoredCredentials.serializer(), plaintext.toString(Charsets.UTF_8))
    } catch (_: SecretCipherException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: SerializationException) {
        null
    }

    private fun keyFor(profileId: String) = stringPreferencesKey("credentials.$profileId")
}
