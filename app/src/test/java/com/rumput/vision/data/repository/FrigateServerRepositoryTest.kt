package com.rumput.vision.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rumput.vision.core.common.Clock
import com.rumput.vision.core.database.RumputDatabase
import com.rumput.vision.data.remote.SessionHarness
import com.rumput.vision.domain.model.CameraStatus
import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.domain.model.FrigateResult
import com.rumput.vision.domain.repository.PasswordUpdate
import com.rumput.vision.testing.FakeFrigateServer
import com.rumput.vision.testing.Fixtures
import com.rumput.vision.testing.profile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrigateServerRepositoryTest {
    private lateinit var db: RumputDatabase
    private val server = FakeFrigateServer()
    private lateinit var harness: SessionHarness
    private var now = System.currentTimeMillis()

    private val repository by lazy {
        FrigateServerRepository(harness.session, harness.profiles, db.cameraDao(), Clock { now })
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), RumputDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        harness = SessionHarness(profile(local = server.url().value))
        // Room enforces the profile foreign key, so the profile must exist in the real table too.
        runBlocking {
            DefaultServerProfileRepository(db.serverProfileDao(), harness.credentials)
                .saveProfile(profile(local = server.url().value), PasswordUpdate.Keep)
        }
        harness.await<ConnectionState.Connected>()
    }

    @After
    fun tearDown() {
        harness.close()
        server.close()
        db.close()
    }

    private fun statsWithFps(fpsByCamera: Map<String, Double>): String {
        var stats = Fixtures.read("stats.json")
        fpsByCamera.forEach { (camera, fps) ->
            stats = stats.replace(Regex("(\"$camera\": \\{[^}]*?\"camera_fps\": )[0-9.]+")) { "${it.groupValues[1]}$fps" }
        }
        return stats
    }

    @Test
    fun `refresh discovers cameras and computes live status`() = runBlocking {
        server.statsOverride = statsWithFps(mapOf("garasi" to 5.0, "halaman" to 0.0, "ruang_belakang" to 5.0, "ruang_tengah" to 5.0))

        assertEquals(FrigateResult.Success(Unit), repository.refresh())

        val cameras = repository.observeCameras().first()
        assertEquals(listOf("garasi", "halaman", "ruang_belakang", "ruang_tengah"), cameras.map { it.camera.name }.sorted())
        val byName = cameras.associateBy { it.camera.name }
        assertEquals(CameraStatus.ONLINE, byName.getValue("garasi").status)
        assertEquals(CameraStatus.OFFLINE, byName.getValue("halaman").status)
        assertTrue(cameras.none { it.isCached })
        assertEquals("Ruang Tengah", byName.getValue("ruang_tengah").camera.displayName)
        assertEquals("garasi_sub", byName.getValue("garasi").camera.liveStreams.single().streamName)
        assertEquals(now, byName.getValue("garasi").lastSeenOnlineEpochMillis)
        assertNull(byName.getValue("halaman").lastSeenOnlineEpochMillis)

        val status = repository.observeServerStatus().first()!!
        assertEquals("0.18.2-cfa08c5", status.version)
    }

    @Test
    fun `cached cameras are shown with unknown status when the server goes away`() = runBlocking {
        server.statsOverride = statsWithFps(mapOf("garasi" to 5.0))
        repository.refresh()

        server.close()
        val result = repository.refresh()

        assertTrue(result is FrigateResult.Failure)
        val cameras = repository.observeCameras().first()
        assertEquals(4, cameras.size)
        assertTrue(cameras.all { it.status == CameraStatus.UNKNOWN && it.isCached })
        // "Last seen" survives even though live status is unknown.
        assertEquals(now, cameras.first { it.camera.name == "garasi" }.lastSeenOnlineEpochMillis)
        assertNull(repository.observeServerStatus().first())
    }

    @Test
    fun `stale live data is not shown as online`() = runBlocking {
        server.statsOverride = statsWithFps(mapOf("garasi" to 5.0))
        repository.refresh()

        now += 3 * 60 * 1000 // three minutes later without a refresh
        val garasi = repository.observeCameras().first().first { it.camera.name == "garasi" }

        assertEquals(CameraStatus.UNKNOWN, garasi.status)
    }

    @Test
    fun `cameras removed from Frigate config disappear from the cache`() = runBlocking {
        repository.refresh()
        val config = Fixtures.read("config.json")
        // Drop "halaman" from the cameras map by renaming its key to something the repository will not see.
        server.statsOverride = null
        val trimmed = com.rumput.vision.core.network.FrigateJson.parseToJsonElement(config).let { root ->
            val obj = root as kotlinx.serialization.json.JsonObject
            val cameras = (obj.getValue("cameras") as kotlinx.serialization.json.JsonObject).filterKeys { it != "halaman" }
            kotlinx.serialization.json.JsonObject(obj + ("cameras" to kotlinx.serialization.json.JsonObject(cameras)))
        }.toString()
        server.configOverride = trimmed
        repository.refresh()

        assertEquals(3, repository.observeCameras().first().size)
    }
}
