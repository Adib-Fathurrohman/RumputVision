package com.rumput.vision.data.remote

import com.rumput.vision.core.common.ApplicationScope
import com.rumput.vision.core.common.Clock
import com.rumput.vision.core.logging.AppLogger
import com.rumput.vision.core.network.ForegroundMonitor
import com.rumput.vision.core.network.NetworkMonitor
import com.rumput.vision.core.network.NetworkStatus
import com.rumput.vision.core.security.CredentialStore
import com.rumput.vision.core.streaming.MediaCallFactoryProvider
import com.rumput.vision.core.streaming.StreamClientProvider
import com.rumput.vision.core.security.StoredCredentials
import com.rumput.vision.data.remote.auth.BearerTokenInterceptor
import com.rumput.vision.data.remote.auth.FrigateLoginService
import com.rumput.vision.data.remote.auth.LoginResult
import com.rumput.vision.data.remote.auth.ReauthenticatingAuthenticator
import com.rumput.vision.domain.model.AuthMode
import com.rumput.vision.domain.model.ConnectionRoute
import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.domain.model.FrigateError
import com.rumput.vision.domain.model.FrigateResult
import com.rumput.vision.domain.model.NetworkFailure
import com.rumput.vision.domain.model.ServerProfile
import com.rumput.vision.domain.model.ServerUrl
import com.rumput.vision.domain.model.UnauthorizedReason
import com.rumput.vision.domain.repository.ServerConnection
import com.rumput.vision.domain.repository.ServerProfileRepository
import com.rumput.vision.domain.repository.SignInResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/** A resolved endpoint with an API client bound to one profile's session. */
internal class ActiveEndpoint(
    val profileId: String,
    val route: ConnectionRoute,
    val url: ServerUrl,
    val client: OkHttpClient,
    val api: FrigateApi,
)

/**
 * Owns the connection to the active profile's server.
 *
 * Resolution runs only when the active profile, the network, or app foreground state changes,
 * or when [reconnect] is called. There is no background polling.
 */
@Singleton
class FrigateSession @Inject constructor(
    private val profiles: ServerProfileRepository,
    private val credentials: CredentialStore,
    private val networkMonitor: NetworkMonitor,
    private val foregroundMonitor: ForegroundMonitor,
    private val probe: EndpointProbe,
    private val loginService: FrigateLoginService,
    private val apiFactory: FrigateApiFactory,
    private val baseClient: OkHttpClient,
    private val clock: Clock,
    @param:ApplicationScope private val scope: CoroutineScope,
) : ServerConnection, StreamClientProvider, MediaCallFactoryProvider {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Unknown)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    @Volatile
    private var active: ActiveEndpoint? = null

    @Volatile
    private var lastResolveAtMillis = 0L

    private val reconnectRequests = MutableStateFlow(0)
    private val resolveMutex = Mutex()

    init {
        scope.launch {
            combine(
                profiles.observeActiveProfile(),
                settledNetworkStatus(),
                foregroundMonitor.isForeground,
                reconnectRequests,
            ) { profile, network, foreground, _ -> Trigger(profile, network, foreground) }
                .collectLatest { trigger -> onTrigger(trigger) }
        }
    }

    /**
     * A network switch arrives as several capability callbacks in quick succession; wait for it to
     * settle instead of resolving once per callback. The first value passes through immediately.
     */
    @OptIn(FlowPreview::class)
    private fun settledNetworkStatus(): Flow<NetworkStatus> {
        var first = true
        return networkMonitor.status
            .debounce { if (first) { first = false; 0L } else NETWORK_SETTLE_MILLIS }
            .onEach { status ->
                // Pooled connections belong to the previous network and would only fail on first use.
                if (status.networkId != lastNetworkId) {
                    if (lastNetworkId != null) baseClient.connectionPool.evictAll()
                    lastNetworkId = status.networkId
                }
            }
    }

    @Volatile
    private var lastNetworkId: String? = null

    private data class Trigger(val profile: ServerProfile?, val network: NetworkStatus, val foreground: Boolean)

    private suspend fun onTrigger(trigger: Trigger) {
        val profile = trigger.profile
        when {
            profile == null -> {
                active = null
                _state.value = ConnectionState.NotConfigured
            }
            // Keep the last known state while in the background; nothing is done until the app returns.
            !trigger.foreground -> Unit
            !trigger.network.isConnected -> {
                if (_state.value !is ConnectionState.Unreachable) AppLogger.w { "Device has no network" }
                active = null
                _state.value = ConnectionState.Unreachable(FrigateError.Unreachable(NetworkFailure.NO_NETWORK))
            }
            else -> resolve(profile, trigger.network)
        }
    }

    /** Base URL of the connected endpoint, or null when not connected. */
    val connectedBaseUrl: ServerUrl?
        get() = active?.takeIf { _state.value is ConnectionState.Connected }?.url

    /**
     * Call factory for media (images, later video). Requests under the connected endpoint's base URL use
     * its authenticated client; anything else gets the base client, so credentials never leak elsewhere.
     */
    val mediaCallFactory: Call.Factory = Call.Factory { request -> mediaClientFor(request.url).newCall(request) }

    override fun mediaCallFactory(): Call.Factory = mediaCallFactory

    /** The authenticated client for URLs under the connected endpoint; the plain base client for anything else. */
    override fun mediaClientFor(url: HttpUrl): OkHttpClient {
        val endpoint = active
        return if (endpoint != null && endpoint.url.contains(url)) endpoint.client else baseClient
    }

    override fun reconnect() {
        reconnectRequests.update { it + 1 }
    }

    private suspend fun resolve(profile: ServerProfile, network: NetworkStatus) = resolveMutex.withLock {
        lastResolveAtMillis = clock.nowEpochMillis()
        _state.value = ConnectionState.Connecting
        active = null

        val candidates = EndpointSelector.candidates(profile, network)
        var lastError: FrigateError = FrigateError.NotConfigured
        var chosen: Pair<EndpointCandidate, String?>? = null
        for (candidate in candidates) {
            when (val result = probe.probe(candidate.url)) {
                is ProbeResult.Frigate -> { chosen = candidate to result.version; break }
                ProbeResult.AuthRequired -> { chosen = candidate to null; break }
                is ProbeResult.Redirect -> lastError = FrigateError.Http(302)
                ProbeResult.NotFrigate -> lastError = FrigateError.InvalidResponse
                is ProbeResult.Failed -> lastError = result.error
            }
        }
        val (candidate, probedVersion) = chosen ?: run {
            AppLogger.w { "No endpoint reachable (${candidates.joinToString { it.route.name }}): $lastError" }
            _state.value = ConnectionState.Unreachable(lastError)
            return@withLock
        }

        val client = clientFor(profile, candidate.url)
        val endpoint = ActiveEndpoint(profile.id, candidate.route, candidate.url, client, apiFactory.create(candidate.url, client))

        if (profile.authMode == AuthMode.FRIGATE_LOGIN) {
            ensureToken(profile, candidate.url)?.let { reason ->
                AppLogger.w { "Sign-in needed on ${candidate.route}: $reason" }
                _state.value = ConnectionState.Unauthorized(reason)
                return@withLock
            }
        }

        try {
            val user = endpoint.api.profile()
            val version = probedVersion ?: runCatching { endpoint.api.version().string().trim() }.getOrNull()
            active = endpoint
            _state.value = ConnectionState.Connected(
                route = candidate.route,
                serverUrl = candidate.url,
                serverVersion = version,
                anonymousAdmin = user.username == ANONYMOUS && user.role == ADMIN_ROLE,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _state.value = stateFor(e.toFrigateError(), profile)
        }
    }

    /** Signs in with the remembered password when there is no token. Returns a reason if that is impossible. */
    private suspend fun ensureToken(profile: ServerProfile, url: ServerUrl): UnauthorizedReason? {
        val stored = credentials.read(profile.id)
        if (stored?.accessToken != null) return null
        val password = stored?.password ?: return UnauthorizedReason.SIGN_IN_REQUIRED
        return when (val result = loginService.login(url, profile.username.orEmpty(), password)) {
            is LoginResult.Success -> {
                saveToken(profile.id, result)
                null
            }
            LoginResult.InvalidCredentials -> {
                credentials.delete(profile.id)
                UnauthorizedReason.INVALID_CREDENTIALS
            }
            LoginResult.RateLimited -> UnauthorizedReason.RATE_LIMITED
            else -> UnauthorizedReason.SIGN_IN_REQUIRED
        }
    }

    private fun clientFor(profile: ServerProfile, url: ServerUrl): OkHttpClient =
        if (profile.authMode == AuthMode.FRIGATE_LOGIN) {
            baseClient.newBuilder()
                .addInterceptor(BearerTokenInterceptor(profile.id, credentials))
                .authenticator(ReauthenticatingAuthenticator(profile.id, url, profile.username.orEmpty(), credentials, loginService))
                .build()
        } else {
            baseClient
        }

    private fun stateFor(error: FrigateError, profile: ServerProfile): ConnectionState = when (error) {
        FrigateError.Unauthorized -> ConnectionState.Unauthorized(
            if (profile.authMode == AuthMode.NONE) UnauthorizedReason.SERVER_REQUIRES_LOGIN else UnauthorizedReason.SIGN_IN_REQUIRED,
        )
        FrigateError.RateLimited -> ConnectionState.Unauthorized(UnauthorizedReason.RATE_LIMITED)
        else -> ConnectionState.Unreachable(error)
    }

    /**
     * Runs [block] against the active endpoint. Waits for an in-progress resolution; if the server was
     * unreachable, re-resolves at most every [RETRY_INTERVAL_MILLIS] instead of hammering it.
     */
    suspend fun <T> execute(block: suspend (FrigateApi) -> T): FrigateResult<T> {
        val endpoint = awaitEndpoint() ?: return FrigateResult.Failure(currentError())
        return try {
            FrigateResult.Success(block(endpoint.api))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val error = e.toFrigateError()
            onCallFailed(endpoint, error, e)
            FrigateResult.Failure(error)
        }
    }

    private suspend fun awaitEndpoint(): ActiveEndpoint? {
        val current = _state.value
        val retryDue = clock.nowEpochMillis() - lastResolveAtMillis >= RETRY_INTERVAL_MILLIS
        if ((current is ConnectionState.Unreachable || current == ConnectionState.Unknown) && retryDue) {
            reconnect()
            // Let the trigger collector observe the request before waiting for the result.
            withTimeoutOrNull(1_000) { _state.first { it == ConnectionState.Connecting } }
        }
        withTimeoutOrNull(RESOLVE_TIMEOUT_MILLIS) { _state.first { it != ConnectionState.Connecting } }
        return active?.takeIf { _state.value is ConnectionState.Connected }
    }

    private fun onCallFailed(endpoint: ActiveEndpoint, error: FrigateError, cause: Throwable) {
        if (active !== endpoint) return
        when {
            error == FrigateError.Unauthorized || error == FrigateError.RateLimited -> scope.launch {
                val profile = profiles.getProfile(endpoint.profileId) ?: return@launch
                active = null
                _state.value = stateFor(error, profile)
            }
            // HTTP errors from a reachable server do not mean the connection is gone.
            cause is HttpException || error == FrigateError.InvalidResponse -> Unit
            else -> {
                AppLogger.w { "Connection lost on ${endpoint.route}: $error" }
                active = null
                _state.value = ConnectionState.Unreachable(error)
            }
        }
    }

    private fun currentError(): FrigateError = when (val s = _state.value) {
        is ConnectionState.Unreachable -> s.error
        is ConnectionState.Unauthorized -> FrigateError.Unauthorized
        ConnectionState.NotConfigured -> FrigateError.NotConfigured
        else -> FrigateError.Unreachable(NetworkFailure.OTHER)
    }

    override suspend fun signIn(password: String, rememberPassword: Boolean): SignInResult {
        val profile = profiles.observeActiveProfile().firstOrNull()
            ?: return SignInResult.Failed(FrigateError.NotConfigured)
        val network = networkMonitor.status.first()
        val candidate = EndpointSelector.candidates(profile, network).firstOrNull { probe.probe(it.url).isServer() }
            ?: return SignInResult.Failed(currentError().takeUnless { it == FrigateError.Unauthorized } ?: FrigateError.Unreachable(NetworkFailure.OTHER))

        return when (val result = loginService.login(candidate.url, profile.username.orEmpty(), password)) {
            is LoginResult.Success -> {
                credentials.update(profile.id) {
                    StoredCredentials(
                        password = if (rememberPassword) password else null,
                        accessToken = result.token,
                        accessTokenExpiresAtEpochSeconds = result.expiresAtEpochSeconds,
                    )
                }
                reconnect()
                SignInResult.Success
            }
            LoginResult.InvalidCredentials -> SignInResult.InvalidCredentials
            LoginResult.RateLimited -> SignInResult.RateLimited
            LoginResult.NoTokenReturned -> SignInResult.Failed(FrigateError.InvalidResponse)
            is LoginResult.Failed -> SignInResult.Failed(result.error)
        }
    }

    override suspend fun signOut() {
        val endpoint = active
        if (endpoint != null) {
            // Best effort: the server clears its cookie; the token itself expires on its own.
            execute { api -> api.logout() }
        }
        val profile = profiles.observeActiveProfile().firstOrNull() ?: return
        credentials.delete(profile.id)
        active = null
        _state.value = ConnectionState.Unauthorized(UnauthorizedReason.SIGN_IN_REQUIRED)
    }

    private suspend fun saveToken(profileId: String, result: LoginResult.Success) {
        credentials.update(profileId) {
            (it ?: StoredCredentials()).copy(
                accessToken = result.token,
                accessTokenExpiresAtEpochSeconds = result.expiresAtEpochSeconds,
            )
        }
    }

    private fun ProbeResult.isServer() = this is ProbeResult.Frigate || this == ProbeResult.AuthRequired

    private companion object {
        const val ANONYMOUS = "anonymous"
        const val ADMIN_ROLE = "admin"
        const val RETRY_INTERVAL_MILLIS = 10_000L
        const val RESOLVE_TIMEOUT_MILLIS = 30_000L
        const val NETWORK_SETTLE_MILLIS = 750L
    }
}

/** True if [url] is on the same origin as this base URL and under its path. */
internal fun ServerUrl.contains(url: HttpUrl): Boolean {
    val base = "$value/".toHttpUrl()
    return url.scheme == base.scheme && url.host == base.host && url.port == base.port &&
        url.encodedPath.startsWith(base.encodedPath)
}
