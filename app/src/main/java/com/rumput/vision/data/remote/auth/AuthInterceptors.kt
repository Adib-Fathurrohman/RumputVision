package com.rumput.vision.data.remote.auth

import com.rumput.vision.core.security.CredentialStore
import com.rumput.vision.core.security.StoredCredentials
import com.rumput.vision.domain.model.ServerUrl
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Attaches `Authorization: Bearer <token>` and stores a refreshed token when Frigate re-issues
 * its session cookie (it does so once the token is older than `auth.refresh_time`).
 * runBlocking is safe here: OkHttp calls interceptors on its own worker threads, and the store is cached.
 */
class BearerTokenInterceptor(
    private val profileId: String,
    private val credentials: CredentialStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { credentials.read(profileId)?.accessToken }
        val request = if (token != null && chain.request().header(AUTHORIZATION) == null) {
            chain.request().newBuilder().header(AUTHORIZATION, "Bearer $token").build()
        } else {
            chain.request()
        }
        val response = chain.proceed(request)
        if (response.isSuccessful) {
            SessionToken.fromSetCookie(response.request.url, response.headers)
                ?.takeIf { it != token }
                ?.let { refreshed ->
                    runBlocking {
                        credentials.update(profileId) {
                            (it ?: StoredCredentials()).copy(
                                accessToken = refreshed,
                                accessTokenExpiresAtEpochSeconds = SessionToken.expiresAtEpochSeconds(refreshed),
                            )
                        }
                    }
                }
        }
        return response
    }
}

/**
 * On 401, signs in again once with the remembered password and retries. Without a remembered
 * password, or if the retry also fails, it gives up and the caller surfaces "sign-in required".
 */
class ReauthenticatingAuthenticator(
    private val profileId: String,
    private val baseUrl: ServerUrl,
    private val username: String,
    private val credentials: CredentialStore,
    private val loginService: FrigateLoginService,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.url.encodedPath.trimEnd('/').endsWith("/api/login")) return null
        if (response.priorResponse != null) return null // Already retried once.

        return runBlocking {
            val stored = credentials.read(profileId)
            val sentToken = response.request.header(AUTHORIZATION)?.removePrefix("Bearer ")
            // Another request may already have refreshed the token while this one was in flight.
            stored?.accessToken?.takeIf { it != sentToken }?.let { fresh ->
                return@runBlocking response.request.withBearer(fresh)
            }
            val password = stored?.password
            if (password == null) {
                credentials.update(profileId) { it?.copy(accessToken = null, accessTokenExpiresAtEpochSeconds = null) }
                return@runBlocking null
            }
            when (val result = loginService.login(baseUrl, username, password)) {
                is LoginResult.Success -> {
                    credentials.update(profileId) {
                        (it ?: StoredCredentials()).copy(
                            accessToken = result.token,
                            accessTokenExpiresAtEpochSeconds = result.expiresAtEpochSeconds,
                        )
                    }
                    response.request.withBearer(result.token)
                }
                LoginResult.InvalidCredentials -> {
                    // The remembered password no longer works: forget it rather than retrying forever.
                    credentials.delete(profileId)
                    null
                }
                else -> null
            }
        }
    }

    private fun Request.withBearer(token: String) = newBuilder().header(AUTHORIZATION, "Bearer $token").build()
}

private const val AUTHORIZATION = "Authorization"
