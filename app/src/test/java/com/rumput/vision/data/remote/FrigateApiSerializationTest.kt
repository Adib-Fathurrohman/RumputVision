package com.rumput.vision.data.remote

import com.rumput.vision.data.mapper.cameraDisplayName
import com.rumput.vision.data.mapper.toServerStatus
import com.rumput.vision.testing.FakeFrigateServer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parses responses recorded from the real Frigate 0.18.2 server (sanitized). */
class FrigateApiSerializationTest {
    private val server = FakeFrigateServer()
    private val api = FrigateApiFactory().create(server.url(), OkHttpClient())

    @After fun tearDown() = server.close()

    @Test
    fun `config yields dynamic cameras with live streams and stream names only`() = runBlocking {
        val config = api.config()

        assertEquals(setOf("garasi", "halaman", "ruang_belakang", "ruang_tengah"), config.cameras.keys)
        val garasi = config.cameras.getValue("garasi")
        assertTrue(garasi.enabled)
        assertEquals(mapOf("garasi_sub" to "garasi_sub"), garasi.live?.streams)
        assertEquals(2560, garasi.detect?.width)
        assertTrue(garasi.record?.enabled == true)
        assertFalse(garasi.detect?.enabled == true)
        assertTrue(config.go2rtc!!.streams.keys.containsAll(listOf("garasi_main", "garasi_sub")))
        assertEquals("frigate_token", config.auth?.cookieName)
    }

    @Test
    fun `stats map to server status`() = runBlocking {
        val stats = api.stats()
        assertEquals(4, stats.cameras.size)
        assertTrue(stats.cameras.getValue("garasi").cameraFps >= 0.0)

        val status = stats.toServerStatus(fallbackVersion = null, now = 1L)
        assertEquals("0.18.2-cfa08c5", status.version)
        assertEquals("0.18.3", status.latestVersion)
        assertTrue(status.updateAvailable)
        assertEquals(listOf("dummy"), status.detectors.map { it.name })
        val storage = assertNotNull(status.recordingStorage).let { status.recordingStorage!! }
        assertTrue(storage.totalMb > storage.usedMb)
    }

    @Test
    fun `profile and version`() = runBlocking {
        val profile = api.profile()
        assertEquals("anonymous", profile.username)
        assertEquals("admin", profile.role)
        assertEquals("0.18.2-cfa08c5", api.version().string().trim())
    }

    @Test
    fun `camera display names`() {
        assertEquals("Ruang Tengah", cameraDisplayName("ruang_tengah"))
        assertEquals("Garasi", cameraDisplayName("garasi"))
        assertEquals("Front Door 2", cameraDisplayName("front-door_2"))
    }
}
