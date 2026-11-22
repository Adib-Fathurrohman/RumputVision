package com.rumput.vision.data.remote

import com.rumput.vision.core.common.Clock
import com.rumput.vision.core.network.NetworkStatus
import com.rumput.vision.data.remote.auth.FrigateLoginService
import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.domain.model.ServerProfile
import com.rumput.vision.domain.repository.PasswordUpdate
import com.rumput.vision.testing.FakeCredentialStore
import com.rumput.vision.testing.FakeForegroundMonitor
import com.rumput.vision.testing.FakeNetworkMonitor
import com.rumput.vision.testing.FakeServerProfileRepository
import com.rumput.vision.testing.TestTls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Wires a real [FrigateSession] to fakes for profile, network and foreground, using real HTTP(S). */
class SessionHarness(
    profile: ServerProfile?,
    network: NetworkStatus = FakeNetworkMonitor.WIFI,
    foreground: Boolean = true,
) : AutoCloseable {
    val profiles = FakeServerProfileRepository()
    val credentials = FakeCredentialStore()
    val network = FakeNetworkMonitor(network)
    val foreground = FakeForegroundMonitor(foreground)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client = TestTls.clientTrustingTestCa()

    init {
        if (profile != null) runBlocking { profiles.saveProfile(profile.copy(isActive = true), PasswordUpdate.Keep) }
    }

    val loginService = FrigateLoginService(client, Dispatchers.IO)

    val session = FrigateSession(
        profiles = profiles,
        credentials = credentials,
        networkMonitor = this.network,
        foregroundMonitor = this.foreground,
        probe = EndpointProbe(client, Dispatchers.IO),
        loginService = loginService,
        apiFactory = FrigateApiFactory(),
        baseClient = client,
        clock = Clock.System,
        scope = scope,
    )

    fun awaitState(predicate: (ConnectionState) -> Boolean): ConnectionState = runBlocking {
        withTimeout(15_000) { session.state.first(predicate) }
    }

    inline fun <reified T : ConnectionState> await(): T = awaitState { it is T } as T

    override fun close() = scope.cancel()
}
