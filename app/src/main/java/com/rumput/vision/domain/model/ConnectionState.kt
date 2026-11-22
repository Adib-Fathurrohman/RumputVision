package com.rumput.vision.domain.model

sealed interface ConnectionState {
    /** No server profile configured yet. */
    data object NotConfigured : ConnectionState

    data object Connecting : ConnectionState

    data class Connected(
        val route: ConnectionRoute,
        val serverUrl: ServerUrl,
        val serverVersion: String?,
        /** The server answered as "anonymous" with the admin role: authentication is bypassed. */
        val anonymousAdmin: Boolean,
    ) : ConnectionState

    /** The server needs a sign-in (no stored session, or the session and password were rejected). */
    data class Unauthorized(val reason: UnauthorizedReason) : ConnectionState

    data class Unreachable(val error: FrigateError) : ConnectionState

    /** Not resolved yet (e.g. the app has not been in the foreground since the profile was selected). */
    data object Unknown : ConnectionState
}

enum class UnauthorizedReason {
    SIGN_IN_REQUIRED,
    INVALID_CREDENTIALS,
    RATE_LIMITED,

    /** The profile is set to "no authentication" but the server demands it. */
    SERVER_REQUIRES_LOGIN,
}

enum class ConnectionRoute { LOCAL, REMOTE }
