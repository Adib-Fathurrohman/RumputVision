package com.rumput.vision.domain.model

sealed interface ConnectionState {
    /** No server profile configured yet. */
    data object NotConfigured : ConnectionState

    data object Connecting : ConnectionState

    data class Connected(val route: ConnectionRoute, val serverVersion: String?) : ConnectionState

    /** The server rejected the credentials, or a sign-in is required. */
    data object Unauthorized : ConnectionState

    data class Unreachable(val reason: String?) : ConnectionState

    /** Connection handling exists but has not run yet (e.g. before the first probe). */
    data object Unknown : ConnectionState
}

enum class ConnectionRoute { LOCAL, REMOTE }
