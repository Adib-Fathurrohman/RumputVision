package com.rumput.vision.domain.repository

import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.domain.model.FrigateError
import kotlinx.coroutines.flow.StateFlow

/** Connection to the active profile's Frigate server. Resolves local/remote endpoints and keeps the session. */
interface ServerConnection {
    val state: StateFlow<ConnectionState>

    /** Re-resolve the endpoint and session now (e.g. user pressed retry). */
    fun reconnect()

    suspend fun signIn(password: String, rememberPassword: Boolean): SignInResult

    /** Ends the server session (best effort) and forgets the token and password on this device. */
    suspend fun signOut()
}

sealed interface SignInResult {
    data object Success : SignInResult
    data object InvalidCredentials : SignInResult
    data object RateLimited : SignInResult
    data class Failed(val error: FrigateError) : SignInResult
}
