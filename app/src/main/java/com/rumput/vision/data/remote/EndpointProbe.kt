package com.rumput.vision.data.remote

import com.rumput.vision.core.common.IoDispatcher
import com.rumput.vision.core.network.NetworkStatus
import com.rumput.vision.domain.model.ConnectionRoute
import com.rumput.vision.domain.model.EndpointPreference
import com.rumput.vision.domain.model.FrigateError
import com.rumput.vision.domain.model.ServerProfile
import com.rumput.vision.domain.model.ServerUrl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class EndpointCandidate(val route: ConnectionRoute, val url: ServerUrl)

object EndpointSelector {
    /**
     * Order in which to try a profile's URLs. On Wi-Fi/Ethernet the local URL goes first; on mobile
     * data the remote URL goes first, but the local one is still tried (it may work over a VPN).
     */
    fun candidates(profile: ServerProfile, network: NetworkStatus): List<EndpointCandidate> {
        val local = profile.localUrl?.let { EndpointCandidate(ConnectionRoute.LOCAL, it) }
        val remote = profile.remoteUrl?.let { EndpointCandidate(ConnectionRoute.REMOTE, it) }
        return when (profile.endpointPreference) {
            EndpointPreference.LOCAL_ONLY -> listOfNotNull(local)
            EndpointPreference.REMOTE_ONLY -> listOfNotNull(remote)
            EndpointPreference.AUTOMATIC ->
                if (network.isLocalCapable) listOfNotNull(local, remote) else listOfNotNull(remote, local)
        }
    }
}

sealed interface ProbeResult {
    /** `GET /api/version` answered with a Frigate version. */
    data class Frigate(val version: String) : ProbeResult

    /** `GET /api/version` returned 401: a Frigate (or its auth proxy) that requires sign-in. */
    data object AuthRequired : ProbeResult

    data class Redirect(val location: String?) : ProbeResult

    /** Something answered, but it is not Frigate's API (e.g. a web page). */
    data object NotFrigate : ProbeResult

    data class Failed(val error: FrigateError) : ProbeResult
}

/** Cheap, unauthenticated reachability check with short timeouts. */
class EndpointProbe @Inject constructor(
    baseClient: OkHttpClient,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val client = baseClient.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun probe(url: ServerUrl): ProbeResult = withContext(io) {
        val request = Request.Builder().url("${url.value}/api/version").get().build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isRedirect -> ProbeResult.Redirect(response.header("Location"))
                    response.code == 401 -> ProbeResult.AuthRequired
                    response.isSuccessful -> {
                        val body = response.body.string().take(MAX_VERSION_LENGTH).trim()
                        if (VERSION.matches(body)) ProbeResult.Frigate(body) else ProbeResult.NotFrigate
                    }
                    response.code == 404 -> ProbeResult.NotFrigate
                    else -> ProbeResult.Failed(httpError(response.code))
                }
            }
        } catch (e: IOException) {
            ProbeResult.Failed(e.toFrigateError())
        }
    }

    private companion object {
        const val MAX_VERSION_LENGTH = 64
        val VERSION = Regex("""^\d+\.\d+(\.\d+)?[\w.+-]*$""")
    }
}
