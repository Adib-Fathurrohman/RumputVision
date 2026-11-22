package com.rumput.vision.data.remote

import com.rumput.vision.core.common.ApplicationScope
import com.rumput.vision.core.logging.AppLogger
import com.rumput.vision.core.network.WebSocketDispatcher
import com.rumput.vision.core.streaming.StreamClientProvider
import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.domain.model.ServerUrl
import com.rumput.vision.domain.repository.ServerConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A message from Frigate's `/ws`: `{"topic": "...", "payload": ...}`. [payload] is the raw text. */
data class RealtimeMessage(val topic: String, val payload: String)

/**
 * Frigate's realtime WebSocket (`/ws`, verified on 0.18.2: camera status, motion, `reviews`, `stats`, …).
 *
 * The socket is open only while [messages] has subscribers and the server is connected; there is no
 * background connection. Drops reconnect with capped exponential backoff ([backoffMillis]), reset after
 * each successful open.
 */
@Singleton
class FrigateRealtime(
    connection: ServerConnection,
    private val clients: StreamClientProvider,
    scope: CoroutineScope,
    private val backoffMillis: (attempt: Int) -> Long,
) {
    @Inject
    constructor(
        connection: ServerConnection,
        clients: StreamClientProvider,
        @ApplicationScope scope: CoroutineScope,
    ) : this(connection, clients, scope, ::defaultBackoff)

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: SharedFlow<RealtimeMessage> = connection.state
        .map { (it as? ConnectionState.Connected)?.serverUrl }
        .distinctUntilChanged()
        .flatMapLatest { base -> if (base == null) emptyFlow() else reconnecting(base) }
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000))

    private fun reconnecting(base: ServerUrl): Flow<RealtimeMessage> = flow {
        var attempt = 0
        while (true) {
            try {
                emitAll(openOnce(base) { attempt = 0 })
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                AppLogger.w { "Realtime socket dropped (${e::class.java.simpleName})" }
            }
            delay(backoffMillis(attempt++))
        }
    }

    private fun openOnce(base: ServerUrl, onOpened: () -> Unit): Flow<RealtimeMessage> = callbackFlow {
        val httpUrl = "${base.value}/ws".toHttpUrl()
        val client = clients.mediaClientFor(httpUrl).newBuilder()
            .dispatcher(WebSocketDispatcher)
            .pingInterval(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        val socket = client.newWebSocket(
            Request.Builder().url(httpUrl).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    onOpened()
                    // Asks Frigate to send the current state (camera activity, etc.) right away.
                    webSocket.send("""{"topic":"onConnect","payload":""}""")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    parse(text)?.let { trySend(it) }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                    close(IOException("Closed by server ($code)"))
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    close(IOException("Realtime connection failed", t))
                }
            },
        )
        awaitClose { socket.close(1000, null) }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): RealtimeMessage? = runCatching {
            val obj = json.parseToJsonElement(text).jsonObject
            val topic = obj["topic"]?.jsonPrimitive?.content ?: return null
            val payload = when (val p = obj["payload"]) {
                null -> ""
                is JsonPrimitive -> p.content
                else -> p.toString()
            }
            RealtimeMessage(topic, payload)
        }.getOrNull()

        fun defaultBackoff(attempt: Int): Long = (1_000L shl attempt.coerceAtMost(5)).coerceAtMost(30_000L)
    }
}
