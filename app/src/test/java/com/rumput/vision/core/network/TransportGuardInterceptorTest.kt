package com.rumput.vision.core.network

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class TransportGuardInterceptorTest {
    private val server = MockWebServer()
    private val client = baseOkHttpClient()

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.close()

    private fun get(url: String, vararg headers: Pair<String, String>) = Request.Builder().url(url).apply {
        headers.forEach { (k, v) -> header(k, v) }
    }.build()

    @Test
    fun `plain http to a private host without credentials is allowed`() {
        server.enqueue(MockResponse.Builder().body("ok").build())
        client.newCall(get(server.url("/api/version").toString())).execute().use { assertEquals(200, it.code) }
    }

    @Test
    fun `credentials are never sent over plain http`() {
        val url = server.url("/api/profile").toString()
        assertThrows(InsecureTransportException::class.java) {
            client.newCall(get(url, "Authorization" to "Bearer abc")).execute()
        }
        assertThrows(InsecureTransportException::class.java) {
            client.newCall(get(url, "Cookie" to "frigate_token=abc")).execute()
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `login over plain http is blocked before anything is sent`() {
        val request = Request.Builder()
            .url(server.url("/api/login"))
            .post("""{"user":"a","password":"b"}""".toRequestBody("application/json".toMediaType()))
            .build()
        assertThrows(InsecureTransportException::class.java) { client.newCall(request).execute() }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `plain http to a public host is blocked`() {
        assertThrows(InsecureTransportException::class.java) {
            client.newCall(get("http://frigate.example.com/api/version")).execute()
        }
    }

    @Test
    fun `redirects are not followed`() {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "http://evil.example.com/").build())
        client.newCall(get(server.url("/").toString())).execute().use { assertEquals(302, it.code) }
        assertEquals(1, server.requestCount)
    }
}
