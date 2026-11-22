package com.rumput.vision.core.streaming

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.rumput.vision.core.network.WebSocketDispatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Presents go2rtc's MSE WebSocket as a byte stream for Media3's FragmentedMp4Extractor.
 *
 * Protocol (verified against go2rtc 1.9.14 through Frigate 0.18.2):
 *  1. client → `{"type":"mse","value":"<codec list>"}`
 *  2. server → `{"type":"mse","value":"video/mp4; codecs=\"…\""}` (or `{"type":"error",…}`)
 *  3. server → binary init segment (ftyp+moov), then one binary message per moof+mdat fragment.
 *
 * The stream is unbounded and not seekable. If data waits in the queue longer than [maxLagMillis]
 * (the player fell behind real time), reading fails with [StreamFailure.FELL_BEHIND] so the player
 * reconnects at the live edge instead of drifting further behind.
 */
@OptIn(UnstableApi::class)
class MseWebSocketDataSource(
    private val clients: StreamClientProvider,
    private val codecs: String,
    private val openTimeoutMillis: Long = 10_000,
    private val onClosed: ((MseWebSocketDataSource) -> Unit)? = null,
    private val stallTimeoutMillis: Long = 10_000,
    private val maxLagMillis: Long = 4_000,
) : BaseDataSource(/* isNetwork = */ true) {

    private class Message(val bytes: ByteArray, val receivedAtNanos: Long)

    private val queue = LinkedBlockingDeque<Message>()
    private val handshake = CountDownLatch(1)

    @Volatile private var failure: StreamException? = null
    @Volatile private var mimeType: String? = null
    @Volatile private var closedByServer = false
    private val bytesQueued = AtomicLong()

    private var socket: WebSocket? = null
    private var uri: Uri? = null
    private var current: ByteArray? = null
    private var currentOffset = 0
    private var opened = false

    /** Content type announced by go2rtc, e.g. `video/mp4; codecs="hvc1.1.6.L153.B0,flac"`. */
    val negotiatedMimeType: String? get() = mimeType

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val url = dataSpec.uri.toString()
        val httpUrl = url.replaceFirst("ws://", "http://").replaceFirst("wss://", "https://").toHttpUrl()
        val client = clients.mediaClientFor(httpUrl).newBuilder()
            .dispatcher(WebSocketDispatcher)
            .pingInterval(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        socket = client.newWebSocket(Request.Builder().url(url).build(), Listener())

        if (!handshake.await(openTimeoutMillis, TimeUnit.MILLISECONDS)) {
            closeSocket()
            throw StreamException(StreamFailure.NETWORK, "No answer from the stream server")
        }
        failure?.let { closeSocket(); throw it }
        if (mimeType == null) {
            closeSocket()
            throw StreamException(StreamFailure.SOURCE_ERROR, "Stream closed before it started")
        }
        opened = true
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        var chunk = current
        if (chunk == null || currentOffset >= chunk.size) {
            val next = nextMessage() ?: return C.RESULT_END_OF_INPUT
            chunk = next.bytes
            current = chunk
            currentOffset = 0
        }
        val count = minOf(length, chunk.size - currentOffset)
        System.arraycopy(chunk, currentOffset, buffer, offset, count)
        currentOffset += count
        bytesTransferred(count)
        return count
    }

    private fun nextMessage(): Message? {
        failure?.let { throw it }
        val message = queue.poll(stallTimeoutMillis, TimeUnit.MILLISECONDS)
        // A failure may have woken this poll with an empty sentinel message.
        failure?.let { throw it }
        if (message == null || message.bytes.isEmpty()) {
            if (closedByServer && queue.isEmpty()) return null
            if (message == null) throw StreamException(StreamFailure.NETWORK, "Stream stalled")
            return nextMessage()
        }
        bytesQueued.addAndGet(-message.bytes.size.toLong())
        val lagMillis = (System.nanoTime() - message.receivedAtNanos) / 1_000_000
        if (lagMillis > maxLagMillis) throw StreamException(StreamFailure.FELL_BEHIND, "Playback fell ${lagMillis}ms behind")
        return message
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        onClosed?.invoke(this)
        closeSocket()
        queue.clear()
        current = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private fun closeSocket() {
        socket?.let { if (!it.close(NORMAL_CLOSURE, "closed")) it.cancel() }
        socket = null
    }

    /**
     * Closes the connection immediately from any thread and wakes a reader blocked in [read].
     * ExoPlayer's stop only takes effect once the loader returns from [read]; without this the
     * WebSocket stayed open for several seconds after the screen was left.
     */
    fun abort() {
        // A close frame lets Frigate's nginx tell go2rtc right away; a bare TCP cancel left the server-side
        // consumer alive for ~6 s. OkHttp cancels the socket itself if the server does not answer.
        socket?.close(NORMAL_CLOSURE, "stopped")
        if (failure == null) failure = StreamException(StreamFailure.NETWORK, "Stream stopped")
        handshake.countDown()
        queue.offer(Message(ByteArray(0), System.nanoTime()))
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val request = buildJsonObject {
                put("type", "mse")
                put("value", codecs)
            }
            webSocket.send(request.toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
            when (json.string("type")) {
                "mse" -> {
                    mimeType = json.string("value")
                    handshake.countDown()
                }
                "error" -> fail(StreamException(StreamFailure.SOURCE_ERROR, json.string("value") ?: "Stream error"))
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (handshake.count > 0L) return // binary data before the mse reply is not expected
            queue.put(Message(bytes.toByteArray(), System.nanoTime()))
            bytesQueued.addAndGet(bytes.size.toLong())
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            closedByServer = true
            webSocket.close(1000, null)
            handshake.countDown()
            queue.offer(Message(ByteArray(0), System.nanoTime()))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val code = response?.code
            fail(
                when (code) {
                    401, 403 -> StreamException(StreamFailure.UNAUTHORIZED, "Stream request rejected ($code)", t)
                    null -> StreamException(StreamFailure.NETWORK, "Stream connection failed", t)
                    else -> StreamException(StreamFailure.SOURCE_ERROR, "Stream request failed ($code)", t)
                },
            )
        }

        private fun fail(e: StreamException) {
            if (failure == null) failure = e
            handshake.countDown()
            // Wake a reader blocked in poll().
            queue.offer(Message(ByteArray(0), System.nanoTime()))
        }
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content

    /**
     * [codecs] is read for every new connection, so a player can switch audio on or off by reconnecting.
     * Tracks its open sources so [abortAll] can close them at once.
     */
    private companion object {
        const val NORMAL_CLOSURE = 1000
    }

    class Factory(private val clients: StreamClientProvider, private val codecs: () -> String) : DataSource.Factory {
        private val open = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<MseWebSocketDataSource, Boolean>())

        override fun createDataSource(): DataSource =
            MseWebSocketDataSource(clients, codecs(), onClosed = { open.remove(it) }).also { open.add(it) }

        fun abortAll() {
            open.toList().forEach { it.abort() }
        }
    }
}

