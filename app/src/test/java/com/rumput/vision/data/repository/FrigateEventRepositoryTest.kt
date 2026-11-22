package com.rumput.vision.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rumput.vision.core.database.RumputDatabase
import com.rumput.vision.data.remote.FrigateRealtime
import com.rumput.vision.data.remote.RealtimeMessage
import com.rumput.vision.data.remote.SessionHarness
import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.domain.model.EventFilter
import com.rumput.vision.domain.model.FrigateResult
import com.rumput.vision.domain.repository.PasswordUpdate
import com.rumput.vision.testing.FakeFrigateServer
import com.rumput.vision.testing.profile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrigateEventRepositoryTest {
    private lateinit var db: RumputDatabase
    private val server = FakeFrigateServer()
    private lateinit var harness: SessionHarness
    private lateinit var repository: FrigateEventRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), RumputDatabase::class.java)
            .allowMainThreadQueries().build()
        harness = SessionHarness(profile(local = server.url().value))
        runBlocking {
            DefaultServerProfileRepository(db.serverProfileDao(), harness.credentials)
                .saveProfile(profile(local = server.url().value), PasswordUpdate.Keep)
        }
        harness.await<ConnectionState.Connected>()
        val realtime = FrigateRealtime(harness.session, harness.session, kotlinx.coroutines.GlobalScope) { 1_000 }
        repository = FrigateEventRepository(harness.session, harness.profiles, db.eventDao(), realtime)
    }

    @After
    fun tearDown() {
        harness.close()
        server.close()
        db.close()
    }

    @Test
    fun `first page is parsed, cached, and paged by start time`() = runBlocking {
        val page = (repository.fetchEvents(EventFilter(), beforeEpochMillis = null, limit = 3) as FrigateResult.Success).value

        assertEquals(3, page.events.size)
        assertTrue(page.events.all { it.label == "orang_terdeteksi" && it.hasClip && it.hasSnapshot })
        assertEquals(page.events.last().startEpochMillis, page.nextBeforeEpochMillis)
        assertEquals(page.events.map { it.id }, repository.observeCachedEvents(EventFilter(), 10).first().map { it.id })
    }

    @Test
    fun `filters and paging become query parameters`() = runBlocking {
        repository.fetchEvents(
            EventFilter(cameras = setOf("halaman", "garasi"), labels = setOf("orang_terdeteksi"), afterEpochMillis = 1_000_000),
            beforeEpochMillis = 2_000_500,
            limit = 50,
        )
        val url = server.requests.last { it.url.encodedPath == "/api/events" }.url
        assertEquals("garasi,halaman", url.queryParameter("cameras"))
        assertEquals("orang_terdeteksi", url.queryParameter("labels"))
        assertEquals("1000.0", url.queryParameter("after"))
        assertEquals("2000.5", url.queryParameter("before"))
        assertEquals("50", url.queryParameter("limit"))
    }

    @Test
    fun `a short page means the end was reached`() = runBlocking {
        val page = (repository.fetchEvents(EventFilter(), null, limit = 50) as FrigateResult.Success).value
        assertNull(page.nextBeforeEpochMillis)
    }

    @Test
    fun `event detail falls back to the cache when the server is gone`() = runBlocking {
        val id = (repository.fetchEvents(EventFilter(), null, 3) as FrigateResult.Success).value.events.first().id
        server.close()
        assertEquals(id, (repository.getEvent(id) as FrigateResult.Success).value.id)
    }

    @Test
    fun `unknown event is a 404`() = runBlocking {
        val result = repository.getEvent("nope")
        assertTrue(result is FrigateResult.Failure)
    }

    @Test
    fun `realtime keys identify event and review changes, ignoring other topics`() {
        val review = """{"type":"end","before":{"id":"r1"},"after":{"id":"r1"}}"""
        assertEquals("reviews:r1:end", activityKey(RealtimeMessage("reviews", review)))
        assertEquals("events:e1:new", activityKey(RealtimeMessage("events", """{"type":"new","after":{"id":"e1"}}""")))
        assertNotEquals(
            activityKey(RealtimeMessage("reviews", """{"type":"new","after":{"id":"r1"}}""")),
            activityKey(RealtimeMessage("reviews", review)),
        )
        assertNull(activityKey(RealtimeMessage("garasi/motion", "ON")))
        assertNull(activityKey(RealtimeMessage("stats", "{}")))
    }
}
