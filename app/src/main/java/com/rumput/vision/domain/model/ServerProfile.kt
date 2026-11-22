package com.rumput.vision.domain.model

/**
 * A configured Frigate server. Contains no secrets: passwords and tokens live in the
 * encrypted credential store, keyed by [id].
 */
data class ServerProfile(
    val id: String,
    val displayName: String,
    val localUrl: ServerUrl?,
    val remoteUrl: ServerUrl?,
    val authMode: AuthMode,
    val username: String?,
    val endpointPreference: EndpointPreference,
    val isActive: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    val urls: List<ServerUrl> get() = listOfNotNull(localUrl, remoteUrl)

    /** True when any configured URL would carry traffic unencrypted. */
    val usesCleartext: Boolean get() = urls.any { !it.isHttps }
}

enum class AuthMode {
    /** Frigate's built-in login (port 8971 / authenticated proxy). Requires HTTPS. */
    FRIGATE_LOGIN,

    /** No credentials sent. Only for servers whose auth is handled elsewhere or disabled. */
    NONE,
}

enum class EndpointPreference {
    /** Prefer the local URL when reachable, otherwise the remote URL. */
    AUTOMATIC,
    LOCAL_ONLY,
    REMOTE_ONLY,
}
