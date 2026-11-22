package com.rumput.vision.core.security

import kotlinx.serialization.Serializable

/** Secrets for one server profile. Never log instances of this class. */
@Serializable
data class StoredCredentials(
    val password: String? = null,
    val accessToken: String? = null,
    val accessTokenExpiresAtEpochSeconds: Long? = null,
) {
    val isEmpty: Boolean get() = password == null && accessToken == null

    override fun toString(): String =
        "StoredCredentials(password=${if (password != null) "***" else "null"}, " +
            "accessToken=${if (accessToken != null) "***" else "null"}, " +
            "accessTokenExpiresAtEpochSeconds=$accessTokenExpiresAtEpochSeconds)"
}

interface CredentialStore {
    suspend fun read(profileId: String): StoredCredentials?

    /** Atomically replaces the credentials; returning null or empty credentials deletes them. */
    suspend fun update(profileId: String, transform: (StoredCredentials?) -> StoredCredentials?)

    suspend fun delete(profileId: String)
}
