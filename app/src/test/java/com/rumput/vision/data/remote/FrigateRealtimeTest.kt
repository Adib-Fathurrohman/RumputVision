package com.rumput.vision.data.remote

import com.rumput.vision.core.streaming.StreamClientProvider
import com.rumput.vision.domain.model.ConnectionRoute
import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.testing.FakeServerConnection
import com.rumput.vision.testing.url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class FrigateRealtimeTest {
    private val server = MockWebServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val received = CopyOnWriteArrayList<String>()
    private val backoffAttempts = CopyOnWriteArrayList<Int>()

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
    }

    /** One connection that answers onConnect with [messages], then closes. */
    private fun enqueueSession(vararg messages: String) {
        server.enqueue(
            MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    received += text
                    messages.forEach(webSocket::send)
                    webSocket.close(1000, "bye")
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }
            }).build(),
        )
    }

    private fun realtime(connection: FakeServerConnection) =
        FrigateRealtime(connection, StreamClientProvider { OkHttpClient() }, scope) { attempt -> backoffAttempts += attempt; 50 }

    private fun connected() = FakeServerConnection(
        ConnectionState.Connected(ConnectionRoute.LOCAL, url(server.url("/").toString().trimEnd('/')), "0.18.2", false),
    )

    @Test
    fun `parses messages and reconnects with backoff after the server closes`() = runBlocking {
        enqueueSession("""{"topic":"reviews","payload":"{\"type\":\"new\",\"after\":{\"id\":\"r1\"}}"}""")
        enqueueSession("""{"topic":"garasi/motion","payload":"ON"}""")
        server.start()

        val messages = withTimeout(10_000) { realtime(connected()).messages.take(2).toList() }

        assertEquals(RealtimeMessage("reviews", """{"type":"new","after":{"id":"r1"}}"""), messages[0])
        assertEquals(RealtimeMessage("garasi/motion", "ON"), messages[1])
        assertEquals("each connection asks for the current state", listOf("""{"topic":"onConnect","payload":""}"""), received.distinct())
        assertTrue("reconnected after a backoff", backoffAttempts.isNotEmpty())
        assertEquals("/ws", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `no socket while the server is not connected`() = runBlocking {
        server.start()
        val rt = realtime(FakeServerConnection(ConnectionState.Unknown))
        val job = scope.launchCollect(rt)
        Thread.sleep(300)
        assertEquals(0, server.requestCount)
        job.cancel()
    }

    @Test
    fun `payload objects and plain strings are both passed as text`() {
        assertEquals(RealtimeMessage("stats", """{"a":1}"""), FrigateRealtime.parse("""{"topic":"stats","payload":{"a":1}}"""))
        assertEquals(RealtimeMessage("x/status/detect", "online"), FrigateRealtime.parse("""{"topic":"x/status/detect","payload":"online"}"""))
        assertEquals(null, FrigateRealtime.parse("not json"))
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 30_000L), listOf(0, 1, 2, 10).map(FrigateRealtime::defaultBackoff))
    }

    private fun CoroutineScope.launchCollect(rt: FrigateRealtime) = launch { rt.messages.collect { } }
}
