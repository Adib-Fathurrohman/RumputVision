package com.rumput.vision.data.remote

import com.rumput.vision.core.network.InsecureTransportException
import com.rumput.vision.domain.model.FrigateError
import com.rumput.vision.domain.model.NetworkFailure
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

fun Throwable.toFrigateError(): FrigateError = when {
    this is HttpException -> httpError(code())
    this is InsecureTransportException -> FrigateError.Unreachable(NetworkFailure.INSECURE_TRANSPORT)
    // OkHttp retries other routes (e.g. IPv6) after a failed handshake; the final exception may be a
    // ConnectException with the TLS failure only attached as suppressed. Certificate problems must
    // never be reported as something else.
    this is IOException && involvesTls() -> FrigateError.Unreachable(NetworkFailure.TLS)
    else -> classify()
}

private fun Throwable.involvesTls(depth: Int = 0): Boolean =
    depth < 8 && (this is SSLException || cause?.involvesTls(depth + 1) == true || suppressed.any { it.involvesTls(depth + 1) })

private fun Throwable.classify(): FrigateError = when (this) {
    is UnknownHostException -> FrigateError.Unreachable(NetworkFailure.HOST_NOT_FOUND)
    is ConnectException, is NoRouteToHostException -> FrigateError.Unreachable(NetworkFailure.CONNECTION_REFUSED)
    is InterruptedIOException -> FrigateError.Unreachable(NetworkFailure.TIMEOUT)
    is IOException -> FrigateError.Unreachable(NetworkFailure.OTHER)
    is SerializationException, is IllegalArgumentException -> FrigateError.InvalidResponse
    else -> FrigateError.Unreachable(NetworkFailure.OTHER)
}

fun httpError(code: Int): FrigateError = when (code) {
    401 -> FrigateError.Unauthorized
    403 -> FrigateError.Forbidden
    429 -> FrigateError.RateLimited
    else -> FrigateError.Http(code)
}
