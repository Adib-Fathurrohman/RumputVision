package com.rumput.vision.data.remote.auth

import com.rumput.vision.core.common.IoDispatcher
import com.rumput.vision.core.network.FrigateJson
import com.rumput.vision.data.remote.dto.LoginRequestDto
import com.rumput.vision.data.remote.httpError
import com.rumput.vision.data.remote.toFrigateError
import com.rumput.vision.domain.model.FrigateError
import com.rumput.vision.domain.model.ServerUrl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject

sealed interface LoginResult {
    data class Success(val token: String, val expiresAtEpochSeconds: Long?) : LoginResult {
        override fun toString(): String = "Success(token=***, expiresAtEpochSeconds=$expiresAtEpochSeconds)"
    }
    data object InvalidCredentials : LoginResult
    data object RateLimited : LoginResult

    /** Login returned 200 but no session cookie: probably not a Frigate login endpoint. */
    data object NoTokenReturned : LoginResult
    data class Failed(val error: FrigateError) : LoginResult
}

/** `POST /api/login`. Uses the base client, so the transport guard blocks any attempt over plain HTTP. */
class FrigateLoginService @Inject constructor(
    private val client: OkHttpClient,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun login(baseUrl: ServerUrl, username: String, password: String): LoginResult = withContext(io) {
        val body = FrigateJson.encodeToString(LoginRequestDto.serializer(), LoginRequestDto(username, password))
            .toRequestBody(JSON)
        val request = Request.Builder().url("${baseUrl.value}/api/login").post(body).build()
        try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    in 200..299 -> {
                        val token = SessionToken.fromSetCookie(response.request.url, response.headers)
                        if (token == null) {
                            LoginResult.NoTokenReturned
                        } else {
                            LoginResult.Success(token, SessionToken.expiresAtEpochSeconds(token))
                        }
                    }
                    400, 401 -> LoginResult.InvalidCredentials
                    429 -> LoginResult.RateLimited
                    else -> LoginResult.Failed(httpError(response.code))
                }
            }
        } catch (e: IOException) {
            LoginResult.Failed(e.toFrigateError())
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
