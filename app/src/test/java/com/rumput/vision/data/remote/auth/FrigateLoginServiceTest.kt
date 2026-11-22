package com.rumput.vision.data.remote.auth

import com.rumput.vision.core.network.baseOkHttpClient
import com.rumput.vision.domain.model.FrigateError
import com.rumput.vision.domain.model.NetworkFailure
import com.rumput.vision.testing.FakeFrigateServer
import com.rumput.vision.testing.TestTls
import com.rumput.vision.testing.closedPortUrl
import com.rumput.vision.testing.jwt
import com.rumput.vision.testing.url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrigateLoginServiceTest {
    private val server = FakeFrigateServer(https = true, requireAuth = true)
    private val service = FrigateLoginService(TestTls.clientTrustingTestCa(), Dispatchers.IO)

    @After fun tearDown() = server.close()

    @Test
    fun `successful login returns the cookie token and its expiry`() = runBlocking {
        server.validToken = jwt(2_000_000_000)
        val result = service.login(server.url(), "admin", "correct horse")

        assertEquals(LoginResult.Success(server.validToken, 2_000_000_000), result)
        val body = server.requests.single().body!!.utf8()
        assertEquals("""{"user":"admin","password":"correct horse"}""", body)
    }

    @Test
    fun `wrong password, rate limit and missing cookie are distinguished`() = runBlocking {
        assertEquals(LoginResult.InvalidCredentials, service.login(server.url(), "admin", "wrong"))
        server.loginStatus = 429
        assertEquals(LoginResult.RateLimited, service.login(server.url(), "admin", "correct horse"))
        server.loginStatus = 200
        assertEquals(LoginResult.NoTokenReturned, service.login(server.url(), "admin", "correct horse"))
        server.loginStatus = 500
        assertEquals(LoginResult.Failed(FrigateError.Http(500)), service.login(server.url(), "admin", "correct horse"))
    }

    @Test
    fun `login over plain http is refused without contacting the server`() = runBlocking {
        FakeFrigateServer(https = false).use { plain ->
            val result = service.login(plain.url(), "admin", "correct horse")
            assertEquals(LoginResult.Failed(FrigateError.Unreachable(NetworkFailure.INSECURE_TRANSPORT)), result)
            assertTrue(plain.requests.isEmpty())
        }
    }

    @Test
    fun `untrusted certificate fails as TLS error and is never bypassed`() = runBlocking {
        val untrusting = FrigateLoginService(baseOkHttpClient(), Dispatchers.IO)
        val result = untrusting.login(server.url(), "admin", "correct horse")
        assertEquals(LoginResult.Failed(FrigateError.Unreachable(NetworkFailure.TLS)), result)
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun `unreachable server`() = runBlocking {
        val https = url(closedPortUrl().value.replace("http://", "https://"))
        val result = service.login(https, "admin", "x")
        assertEquals(LoginResult.Failed(FrigateError.Unreachable(NetworkFailure.CONNECTION_REFUSED)), result)
    }
}
