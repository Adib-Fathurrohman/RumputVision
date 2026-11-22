package com.rumput.vision.data.remote

import com.rumput.vision.data.remote.auth.FrigateLoginService
import com.rumput.vision.domain.model.AccessCheck
import com.rumput.vision.domain.model.AuthMode
import com.rumput.vision.domain.model.ConnectionRoute
import com.rumput.vision.domain.model.EndpointTestOutcome
import com.rumput.vision.domain.model.FrigateError
import com.rumput.vision.domain.model.NetworkFailure
import com.rumput.vision.domain.usecase.ServerProfileDraft
import com.rumput.vision.testing.FakeCredentialStore
import com.rumput.vision.testing.FakeFrigateServer
import com.rumput.vision.testing.TestTls
import com.rumput.vision.testing.closedPortUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrigateConnectionTesterTest {
    private val closeables = mutableListOf<AutoCloseable>()
    @After fun tearDown() = closeables.forEach { it.close() }

    private val client = TestTls.clientTrustingTestCa()
    private val credentials = FakeCredentialStore()
    private val tester = FrigateConnectionTester(
        probe = EndpointProbe(client, Dispatchers.IO),
        loginService = FrigateLoginService(client, Dispatchers.IO),
        apiFactory = FrigateApiFactory(),
        baseClient = client,
        credentials = credentials,
    )

    private fun draft(local: String = "", remote: String = "", auth: AuthMode = AuthMode.NONE, password: String = "") =
        ServerProfileDraft(displayName = "x", localUrl = local, remoteUrl = remote, authMode = auth, username = "admin", password = password)

    @Test
    fun `detects anonymous admin access and reports the version`() = runBlocking {
        val server = FakeFrigateServer().also { closeables += it }
        val result = tester.test(draft(local = server.url().value)).results.single()

        assertEquals(ConnectionRoute.LOCAL, result.route)
        val outcome = result.outcome as EndpointTestOutcome.Reachable
        assertEquals("0.18.2-cfa08c5", outcome.version)
        assertTrue((outcome.access as AccessCheck.Anonymous).isAdmin)
    }

    @Test
    fun `signs in with the typed password and discards the token`() = runBlocking {
        val server = FakeFrigateServer(https = true, requireAuth = true).also { closeables += it }
        val outcome = tester.test(draft(local = server.url().value, auth = AuthMode.FRIGATE_LOGIN, password = "correct horse"))
            .results.single().outcome as EndpointTestOutcome.Reachable

        assertEquals(AccessCheck.SignedIn("admin", "admin"), outcome.access)
        assertEquals("0.18.2-cfa08c5", outcome.version)
        assertTrue(credentials.values.isEmpty())
    }

    @Test
    fun `auth outcomes without or with wrong password, and no-auth profile`() = runBlocking {
        val server = FakeFrigateServer(https = true, requireAuth = true).also { closeables += it }
        fun access(d: ServerProfileDraft) = runBlocking {
            (tester.test(d).results.single().outcome as EndpointTestOutcome.Reachable).access
        }
        assertEquals(AccessCheck.SignInRequired, access(draft(local = server.url().value, auth = AuthMode.FRIGATE_LOGIN)))
        assertEquals(AccessCheck.InvalidCredentials, access(draft(local = server.url().value, auth = AuthMode.FRIGATE_LOGIN, password = "bad")))
        assertEquals(AccessCheck.ServerRequiresLogin, access(draft(local = server.url().value, auth = AuthMode.NONE)))
    }

    @Test
    fun `uses the stored password when editing with a blank field`() = runBlocking {
        val server = FakeFrigateServer(https = true, requireAuth = true).also { closeables += it }
        credentials.update("p1") { com.rumput.vision.core.security.StoredCredentials(password = "correct horse") }
        val d = draft(local = server.url().value, auth = AuthMode.FRIGATE_LOGIN).copy(id = "p1")

        val outcome = tester.test(d).results.single().outcome as EndpointTestOutcome.Reachable
        assertEquals(AccessCheck.SignedIn("admin", "admin"), outcome.access)
    }

    @Test
    fun `web pages, redirects and dead hosts are reported`() = runBlocking {
        val web = MockWebServer().also { closeables += it }
        web.enqueue(MockResponse.Builder().body("<!doctype html><html></html>").build())
        web.enqueue(MockResponse.Builder().code(301).addHeader("Location", "https://nvr.example.ts.net/").build())
        web.start()
        val base = web.url("/").toString().trimEnd('/')

        assertEquals(EndpointTestOutcome.NotFrigate, tester.test(draft(local = base)).results.single().outcome)
        assertEquals(EndpointTestOutcome.Redirected("https://nvr.example.ts.net/"), tester.test(draft(local = base)).results.single().outcome)
        assertEquals(
            EndpointTestOutcome.Failed(FrigateError.Unreachable(NetworkFailure.CONNECTION_REFUSED)),
            tester.test(draft(local = closedPortUrl().value)).results.single().outcome,
        )
    }

    @Test
    fun `URLs that violate the policy are never contacted`() = runBlocking {
        val server = FakeFrigateServer().also { closeables += it }
        val report = tester.test(draft(local = server.url().value, auth = AuthMode.FRIGATE_LOGIN, password = "correct horse"))
        assertTrue(report.results.isEmpty())
        assertFalse(server.requests.isNotEmpty())
    }
}
