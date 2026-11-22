package com.rumput.vision.data.remote.auth

import com.rumput.vision.testing.jwt
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionTokenTest {
    private val url = "https://nvr.example.ts.net/api/login".toHttpUrl()

    @Test
    fun `extracts the frigate_token cookie`() {
        val token = jwt(2_000_000_000)
        val headers = headersOf("Set-Cookie", "other=1; Path=/", "Set-Cookie", "frigate_token=$token; Path=/; HttpOnly")
        assertEquals(token, SessionToken.fromSetCookie(url, headers))
    }

    @Test
    fun `falls back to a single JWT-looking cookie for custom cookie names`() {
        val token = jwt(2_000_000_000)
        assertEquals(token, SessionToken.fromSetCookie(url, headersOf("Set-Cookie", "my_cookie=$token; Path=/")))
        assertNull(SessionToken.fromSetCookie(url, headersOf("Set-Cookie", "session=plain; Path=/")))
        assertNull(SessionToken.fromSetCookie(url, headersOf()))
    }

    @Test
    fun `cleared cookie is not a token`() {
        assertNull(SessionToken.fromSetCookie(url, headersOf("Set-Cookie", "frigate_token=; Max-Age=0")))
    }

    @Test
    fun `reads exp claim`() {
        assertEquals(2_000_000_000L, SessionToken.expiresAtEpochSeconds(jwt(2_000_000_000)))
        assertNull(SessionToken.expiresAtEpochSeconds("not-a-jwt"))
        assertNull(SessionToken.expiresAtEpochSeconds("a.!!!.c"))
    }
}
