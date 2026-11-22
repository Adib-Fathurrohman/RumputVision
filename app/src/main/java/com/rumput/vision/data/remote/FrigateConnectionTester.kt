package com.rumput.vision.data.remote

import com.rumput.vision.core.security.CredentialStore
import com.rumput.vision.data.remote.auth.FrigateLoginService
import com.rumput.vision.data.remote.auth.LoginResult
import com.rumput.vision.domain.model.AccessCheck
import com.rumput.vision.domain.model.AuthMode
import com.rumput.vision.domain.model.ConnectionRoute
import com.rumput.vision.domain.model.ConnectionTestReport
import com.rumput.vision.domain.model.EndpointTestOutcome
import com.rumput.vision.domain.model.EndpointTestResult
import com.rumput.vision.domain.model.FrigateError
import com.rumput.vision.domain.model.ServerUrl
import com.rumput.vision.domain.repository.ConnectionTester
import com.rumput.vision.domain.usecase.ServerProfileDraft
import com.rumput.vision.domain.usecase.ServerUrlPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import javax.inject.Inject

class FrigateConnectionTester @Inject constructor(
    private val probe: EndpointProbe,
    private val loginService: FrigateLoginService,
    private val apiFactory: FrigateApiFactory,
    private val baseClient: OkHttpClient,
    private val credentials: CredentialStore,
) : ConnectionTester {

    override suspend fun test(draft: ServerProfileDraft): ConnectionTestReport = coroutineScope {
        val password = draft.password.ifEmpty { draft.id?.let { credentials.read(it)?.password }.orEmpty() }
        val targets = listOf(ConnectionRoute.LOCAL to draft.localUrl, ConnectionRoute.REMOTE to draft.remoteUrl)
            .mapNotNull { (route, raw) ->
                val parsed = ServerUrlPolicy.parse(raw) as? ServerUrlPolicy.ParseResult.Valid ?: return@mapNotNull null
                // Never test a URL the profile could not save (e.g. login over http).
                if (ServerUrlPolicy.policyError(parsed.url, draft.authMode) != null) return@mapNotNull null
                route to parsed.url
            }
        val results = targets.map { (route, url) ->
            async { EndpointTestResult(route, url.value, testOne(url, draft, password)) }
        }.awaitAll()
        ConnectionTestReport(results)
    }

    private suspend fun testOne(url: ServerUrl, draft: ServerProfileDraft, password: String): EndpointTestOutcome {
        val version = when (val result = probe.probe(url)) {
            is ProbeResult.Frigate -> result.version
            ProbeResult.AuthRequired -> null
            is ProbeResult.Redirect -> return EndpointTestOutcome.Redirected(result.location)
            ProbeResult.NotFrigate -> return EndpointTestOutcome.NotFrigate
            is ProbeResult.Failed -> return EndpointTestOutcome.Failed(result.error)
        }
        val anonymousApi = apiFactory.create(url, baseClient)
        val anonymous = runCatchingApi { anonymousApi.profile() }
        if (anonymous is ApiOutcome.Ok) {
            return EndpointTestOutcome.Reachable(version, AccessCheck.Anonymous(anonymous.value.role))
        }
        val anonymousError = (anonymous as ApiOutcome.Error).error
        if (anonymousError != FrigateError.Unauthorized) {
            return EndpointTestOutcome.Reachable(version, AccessCheck.Failed(anonymousError))
        }

        return when {
            draft.authMode == AuthMode.NONE -> EndpointTestOutcome.Reachable(version, AccessCheck.ServerRequiresLogin)
            password.isEmpty() -> EndpointTestOutcome.Reachable(version, AccessCheck.SignInRequired)
            else -> {
                val (access, signedInVersion) = signInCheck(url, draft.username.trim(), password)
                EndpointTestOutcome.Reachable(version ?: signedInVersion, access)
            }
        }
    }

    /** Signs in, then reads the profile and version with the token. The token is not kept. */
    private suspend fun signInCheck(url: ServerUrl, username: String, password: String): Pair<AccessCheck, String?> =
        when (val login = loginService.login(url, username, password)) {
            is LoginResult.Success -> {
                val client = baseClient.newBuilder()
                    .addInterceptor { chain ->
                        chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer ${login.token}").build())
                    }
                    .build()
                val api = apiFactory.create(url, client)
                when (val me = runCatchingApi { api.profile() }) {
                    is ApiOutcome.Ok -> {
                        val version = (runCatchingApi { api.version().string().trim() } as? ApiOutcome.Ok)?.value
                        AccessCheck.SignedIn(me.value.username, me.value.role) to version
                    }
                    is ApiOutcome.Error -> AccessCheck.Failed(me.error) to null
                }
            }
            LoginResult.InvalidCredentials -> AccessCheck.InvalidCredentials to null
            LoginResult.RateLimited -> AccessCheck.RateLimited to null
            LoginResult.NoTokenReturned -> AccessCheck.Failed(FrigateError.InvalidResponse) to null
            is LoginResult.Failed -> AccessCheck.Failed(login.error) to null
        }

    private sealed interface ApiOutcome<out T> {
        data class Ok<T>(val value: T) : ApiOutcome<T>
        data class Error(val error: FrigateError) : ApiOutcome<Nothing>
    }

    private suspend fun <T> runCatchingApi(block: suspend () -> T): ApiOutcome<T> = try {
        ApiOutcome.Ok(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        ApiOutcome.Error(e.toFrigateError())
    }
}
