package com.rumput.vision.core.streaming

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Runs the data source against a real WebSocket server speaking go2rtc's MSE protocol. */
@RunWith(AndroidJUnit4::class)
class MseWebSocketDataSourceTest {
    private val server = MockWebServer()
    private val clients = StreamClientProvider { OkHttpClient() }
    private val requests = CopyOnWriteArrayList<String>()
    private var serverSocket: WebSocket? = null
    private val serverOpen = CountDownLatch(1)

    private val init = byteArrayOf(0, 0, 0, 8) + "ftyp".toByteArray()
    private val fragment = byteArrayOf(0, 0, 0, 8) + "moof".toByteArray()

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.close()

    private fun goToRespond(onRequest: (WebSocket, String) -> Unit) {
        server.enqueue(
            MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    serverSocket = webSocket
                    serverOpen.countDown()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    requests += text
                    onRequest(webSocket, text)
                }

                // Answer the client's close frame, as go2rtc does.
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }
            }).build(),
        )
    }

    private fun url() = Uri.parse(server.url("/live/mse/api/ws?src=garasi_sub").toString().replace("http://", "ws://"))

    private fun source(stall: Long = 2_000, maxLag: Long = 4_000) =
        MseWebSocketDataSource(clients, "avc1.640029,flac", openTimeoutMillis = 2_000, stallTimeoutMillis = stall, maxLagMillis = maxLag)

    private fun readFully(source: MseWebSocketDataSource, size: Int): ByteArray {
        val out = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val n = source.read(out, offset, size - offset)
            check(n > 0) { "unexpected $n" }
            offset += n
        }
        return out
    }

    private fun expectFailure(failure: StreamFailure, block: () -> Unit) {
        try {
            block()
            fail("expected $failure")
        } catch (e: StreamException) {
            assertEquals(failure, e.failure)
        }
    }

    @Test
    fun `sends the codec request, then streams init and fragments as bytes`() {
        goToRespond { ws, _ ->
            ws.send("""{"type":"mse","value":"video/mp4; codecs=\"avc1.640029\""}""")
            ws.send(init.toByteString())
            ws.send(fragment.toByteString())
        }
        val source = source()

        assertEquals(C.LENGTH_UNSET.toLong(), source.open(DataSpec(url())))

        assertEquals("""{"type":"mse","value":"avc1.640029,flac"}""", requests.single())
        assertEquals("""video/mp4; codecs="avc1.640029"""", source.negotiatedMimeType)
        assertArrayEquals(init + fragment, readFully(source, init.size + fragment.size))
        source.close()
    }

    @Test
    fun `go2rtc error message fails open as a source error`() {
        goToRespond { ws, _ -> ws.send("""{"type":"error","value":"streams: stream not found"}""") }
        expectFailure(StreamFailure.SOURCE_ERROR) { source().open(DataSpec(url())) }
    }

    @Test
    fun `rejected upgrade is unauthorized`() {
        server.enqueue(MockResponse.Builder().code(401).build())
        expectFailure(StreamFailure.UNAUTHORIZED) { source().open(DataSpec(url())) }
    }

    @Test
    fun `no data after the handshake is a stall`() {
        goToRespond { ws, _ -> ws.send("""{"type":"mse","value":"video/mp4"}""") }
        val source = source(stall = 300)
        source.open(DataSpec(url()))
        expectFailure(StreamFailure.NETWORK) { source.read(ByteArray(16), 0, 16) }
    }

    @Test
    fun `data that waited too long forces a reconnect to the live edge`() {
        goToRespond { ws, _ ->
            ws.send("""{"type":"mse","value":"video/mp4"}""")
            ws.send(init.toByteString())
        }
        val source = source(maxLag = 50)
        source.open(DataSpec(url()))
        Thread.sleep(200)
        expectFailure(StreamFailure.FELL_BEHIND) { source.read(ByteArray(16), 0, 16) }
    }

    @Test
    fun `abort wakes a blocked reader immediately`() {
        goToRespond { ws, _ -> ws.send("""{"type":"mse","value":"video/mp4"}""") }
        val source = source(stall = 30_000)
        source.open(DataSpec(url()))
        val started = System.nanoTime()
        Thread { Thread.sleep(150); source.abort() }.start()

        expectFailure(StreamFailure.NETWORK) { source.read(ByteArray(16), 0, 16) }
        assertTrue("read returned after abort", (System.nanoTime() - started) / 1_000_000 < 5_000)
    }

    @Test
    fun `server closing the stream ends the input`() {
        goToRespond { ws, _ ->
            ws.send("""{"type":"mse","value":"video/mp4"}""")
            ws.send(init.toByteString())
            ws.close(1000, "bye")
        }
        val source = source()
        source.open(DataSpec(url()))
        assertArrayEquals(init, readFully(source, init.size))
        assertEquals(C.RESULT_END_OF_INPUT, source.read(ByteArray(16), 0, 16))
        assertTrue(serverOpen.await(1, TimeUnit.SECONDS))
    }
}
