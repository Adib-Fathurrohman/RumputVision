package com.rumput.vision.data.remote.media

import com.rumput.vision.core.security.StoredCredentials
import com.rumput.vision.data.remote.SessionHarness
import com.rumput.vision.domain.model.AuthMode
import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.testing.FakeFrigateServer
import com.rumput.vision.testing.profile
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrigateCameraMediaTest {
    private val closeables = mutableListOf<AutoCloseable>()
    @After fun tearDown() = closeables.reversed().forEach { it.close() }

    @Test
    fun `frame URL is built on the connected endpoint and encodes the camera name`() {
        val server = FakeFrigateServer().also { closeables += it }
        val h = SessionHarness(profile(local = server.url().value)).also { closeables += it }
        val media = FrigateCameraMedia(h.session)
        h.await<ConnectionState.Connected>()

        assertEquals("${server.url().value}/api/garasi/latest.webp?height=360", media.latestFrameUrl("garasi", 360))
        assertEquals(
            "${server.url().value}/api/a%2Fb%20c/latest.webp?height=360",
            media.latestFrameUrl("a/b c", 360),
        )
        assertEquals(
            "${server.url().value.replace("http://", "ws://")}/live/mse/api/ws?src=garasi_sub",
            media.liveStreamUrl("garasi_sub"),
        )
    }

    @Test
    fun `no URL while not connected`() {
        val h = SessionHarness(profile = null).also { closeables += it }
        h.await<ConnectionState.NotConfigured>()
        assertNull(FrigateCameraMedia(h.session).latestFrameUrl("garasi", 360))
    }

    @Test
    fun `media requests carry the session token only to the connected server`() = runBlocking {
        val server = FakeFrigateServer(https = true, requireAuth = true).also { closeables += it }
        val other = FakeFrigateServer(https = true).also { closeables += it }
        val h = SessionHarness(profile(local = server.url().value, authMode = AuthMode.FRIGATE_LOGIN, username = "admin"))
            .also { closeables += it }
        h.credentials.update("p1") { StoredCredentials(password = "correct horse") }
        h.await<ConnectionState.Connected>()
        val factory = h.session.mediaCallFactory

        factory.newCall(Request.Builder().url("${server.url().value}/api/garasi/latest.webp?height=360").build())
            .execute().use { assertEquals(200, it.code) }
        assertEquals("Bearer ${server.validToken}", server.requests.last().headers["Authorization"])

        factory.newCall(Request.Builder().url("${other.url().value}/api/version").build()).execute().close()
        assertNull(other.requests.single().headers["Authorization"])
    }
}
