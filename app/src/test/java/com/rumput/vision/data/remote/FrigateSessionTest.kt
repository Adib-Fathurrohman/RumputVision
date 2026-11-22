package com.rumput.vision.data.remote

import com.rumput.vision.core.security.StoredCredentials
import com.rumput.vision.domain.model.AuthMode
import com.rumput.vision.domain.model.ConnectionRoute
import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.domain.model.FrigateError
import com.rumput.vision.domain.model.FrigateResult
import com.rumput.vision.domain.model.NetworkFailure
import com.rumput.vision.domain.model.UnauthorizedReason
import com.rumput.vision.domain.repository.SignInResult
import com.rumput.vision.testing.FakeFrigateServer
import com.rumput.vision.testing.FakeNetworkMonitor
import com.rumput.vision.testing.NetworkStatusDisconnected
import com.rumput.vision.testing.closedPortUrl
import com.rumput.vision.testing.jwt
import com.rumput.vision.testing.profile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrigateSessionTest {
    private val closeables = mutableListOf<AutoCloseable>()
    private fun <T : AutoCloseable> T.track() = also { closeables += it }

    @After fun tearDown() = closeables.reversed().forEach { it.close() }

    private fun anonymousServer() = FakeFrigateServer().track()
    private fun authServer() = FakeFrigateServer(https = true, requireAuth = true).track()

    private fun loginProfile(server: FakeFrigateServer) = profile(
        local = server.url().value,
        authMode = AuthMode.FRIGATE_LOGIN,
        username = "admin",
    )

    @Test
    fun `anonymous server connects locally and is flagged as anonymous admin`() {
        val server = anonymousServer()
        val h = SessionHarness(profile(local = server.url().value)).track()

        val connected = h.await<ConnectionState.Connected>()

        assertEquals(ConnectionRoute.LOCAL, connected.route)
        assertEquals("0.18.2-cfa08c5", connected.serverVersion)
        assertTrue(connected.anonymousAdmin)
    }

    @Test
    fun `falls back to the remote URL when the local one is unreachable`() {
        val remote = anonymousServer()
        val h = SessionHarness(profile(local = closedPortUrl().value, remote = remote.url().value)).track()

        assertEquals(ConnectionRoute.REMOTE, h.await<ConnectionState.Connected>().route)
    }

    @Test
    fun `on mobile data the remote URL is tried first`() {
        val local = anonymousServer()
        val remote = anonymousServer()
        val h = SessionHarness(
            profile(local = local.url().value, remote = remote.url().value),
            network = FakeNetworkMonitor.CELLULAR,
        ).track()

        assertEquals(ConnectionRoute.REMOTE, h.await<ConnectionState.Connected>().route)
        assertTrue(local.requests.isEmpty())
    }

    @Test
    fun `all URLs unreachable reports the failure`() {
        val h = SessionHarness(profile(local = closedPortUrl().value)).track()
        val state = h.await<ConnectionState.Unreachable>()
        assertEquals(FrigateError.Unreachable(NetworkFailure.CONNECTION_REFUSED), state.error)
    }

    @Test
    fun `no network means no requests`() {
        val server = anonymousServer()
        val h = SessionHarness(profile(local = server.url().value), network = NetworkStatusDisconnected).track()

        val state = h.await<ConnectionState.Unreachable>()
        assertEquals(FrigateError.Unreachable(NetworkFailure.NO_NETWORK), state.error)
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun `nothing happens in the background until the app returns to the foreground`() {
        val server = anonymousServer()
        val h = SessionHarness(profile(local = server.url().value), foreground = false).track()
        Thread.sleep(300)
        assertEquals(ConnectionState.Unknown, h.session.state.value)
        assertTrue(server.requests.isEmpty())

        h.foreground.isForeground.value = true
        h.await<ConnectionState.Connected>()
    }

    @Test
    fun `login profile signs in with the remembered password and stores the token`() {
        val server = authServer()
        val h = SessionHarness(loginProfile(server)).track()
        runBlocking { h.credentials.update("p1") { StoredCredentials(password = "correct horse") } }

        val connected = h.await<ConnectionState.Connected>()

        assertFalse(connected.anonymousAdmin)
        assertEquals(server.validToken, h.credentials.values["p1"]?.accessToken)
        assertTrue("/api/login" in server.paths())
    }

    @Test
    fun `login profile without a password needs a sign-in and does not call login`() {
        val server = authServer()
        val h = SessionHarness(loginProfile(server)).track()

        assertEquals(UnauthorizedReason.SIGN_IN_REQUIRED, h.await<ConnectionState.Unauthorized>().reason)
        assertFalse("/api/login" in server.paths())
    }

    @Test
    fun `a rejected remembered password is forgotten`() {
        val server = authServer()
        val h = SessionHarness(loginProfile(server)).track()
        runBlocking { h.credentials.update("p1") { StoredCredentials(password = "old password") } }

        assertEquals(UnauthorizedReason.INVALID_CREDENTIALS, h.await<ConnectionState.Unauthorized>().reason)
        assertNull(h.credentials.values["p1"])
    }

    @Test
    fun `no-auth profile against a server that requires login`() {
        val server = FakeFrigateServer(requireAuth = true).track()
        val h = SessionHarness(profile(local = server.url().value)).track()

        assertEquals(UnauthorizedReason.SERVER_REQUIRES_LOGIN, h.await<ConnectionState.Unauthorized>().reason)
    }

    @Test
    fun `an expired token is renewed transparently with the remembered password`() = runBlocking {
        val server = authServer()
        val h = SessionHarness(loginProfile(server)).track()
        h.credentials.update("p1") { StoredCredentials(password = "correct horse") }
        h.await<ConnectionState.Connected>()

        server.validToken = jwt(4_200_000_000) // the server no longer accepts the old token
        val result = h.session.execute { it.stats() }

        assertTrue(result is FrigateResult.Success)
        assertEquals(server.validToken, h.credentials.values["p1"]?.accessToken)
        assertTrue(h.session.state.value is ConnectionState.Connected)
    }

    @Test
    fun `an expired token without a remembered password ends in sign-in required`() = runBlocking {
        val server = authServer()
        val h = SessionHarness(loginProfile(server)).track()
        h.credentials.update("p1") { StoredCredentials(accessToken = server.validToken) }
        h.await<ConnectionState.Connected>()

        server.validToken = jwt(4_200_000_000)
        val result = h.session.execute { it.stats() }

        assertEquals(FrigateResult.Failure(FrigateError.Unauthorized), result)
        assertEquals(UnauthorizedReason.SIGN_IN_REQUIRED, h.await<ConnectionState.Unauthorized>().reason)
    }

    @Test
    fun `a refreshed session cookie replaces the stored token`() = runBlocking {
        val server = authServer()
        val h = SessionHarness(loginProfile(server)).track()
        h.credentials.update("p1") { StoredCredentials(accessToken = server.validToken) }
        h.await<ConnectionState.Connected>()

        val refreshed = jwt(4_300_000_000)
        server.refreshTokenTo = refreshed
        h.session.execute { it.stats() }

        assertEquals(refreshed, h.credentials.values["p1"]?.accessToken)
        assertEquals(4_300_000_000L, h.credentials.values["p1"]?.accessTokenExpiresAtEpochSeconds)
        assertTrue(h.session.execute { it.stats() } is FrigateResult.Success)
    }

    @Test
    fun `sign in without remembering stores only the token, then connects`() = runBlocking {
        val server = authServer()
        val h = SessionHarness(loginProfile(server)).track()
        h.await<ConnectionState.Unauthorized>()

        assertEquals(SignInResult.InvalidCredentials, h.session.signIn("nope", rememberPassword = true))
        assertEquals(SignInResult.Success, h.session.signIn("correct horse", rememberPassword = false))

        h.await<ConnectionState.Connected>()
        val stored = h.credentials.values["p1"]!!
        assertNull(stored.password)
        assertEquals(server.validToken, stored.accessToken)
    }

    @Test
    fun `sign out forgets credentials`() = runBlocking {
        val server = authServer()
        val h = SessionHarness(loginProfile(server)).track()
        h.credentials.update("p1") { StoredCredentials(password = "correct horse") }
        h.await<ConnectionState.Connected>()

        h.session.signOut()

        assertNull(h.credentials.values["p1"])
        assertTrue("/api/logout" in server.paths())
        assertTrue(h.session.state.value is ConnectionState.Unauthorized)
    }

    @Test
    fun `no profile means not configured`() {
        val h = SessionHarness(profile = null).track()
        h.await<ConnectionState.NotConfigured>()
    }
}
