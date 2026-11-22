package com.rumput.vision.domain.model

sealed interface FrigateResult<out T> {
    data class Success<T>(val value: T) : FrigateResult<T>
    data class Failure(val error: FrigateError) : FrigateResult<Nothing>
}

inline fun <T, R> FrigateResult<T>.map(transform: (T) -> R): FrigateResult<R> = when (this) {
    is FrigateResult.Success -> FrigateResult.Success(transform(value))
    is FrigateResult.Failure -> this
}

sealed interface FrigateError {
    data object NotConfigured : FrigateError
    data object Unauthorized : FrigateError
    data object Forbidden : FrigateError
    data object RateLimited : FrigateError
    data class Unreachable(val failure: NetworkFailure) : FrigateError
    data class Http(val code: Int) : FrigateError

    /** The server answered, but not like Frigate (e.g. an HTML page or unparseable JSON). */
    data object InvalidResponse : FrigateError
}

enum class NetworkFailure {
    NO_NETWORK,
    HOST_NOT_FOUND,
    TIMEOUT,
    CONNECTION_REFUSED,

    /** Certificate not trusted, hostname mismatch, or handshake failure. Never bypassed. */
    TLS,

    /** Blocked by the app's transport policy (credentials over HTTP, HTTP to a public host). */
    INSECURE_TRANSPORT,
    OTHER,
}
