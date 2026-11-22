package com.rumput.vision.data.remote

import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.domain.model.FrigateResult
import com.rumput.vision.testing.profile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in check against a real Frigate server (no credentials; for an anonymous-access server only):
 *   FRIGATE_LIVE_URL=http://<server-lan-ip> ./gradlew :app:testDebugUnitTest --tests '*LiveFrigateServerTest*'
 * Skipped unless FRIGATE_LIVE_URL is set. Asserts structure only; nothing from the server is printed.
 */
class LiveFrigateServerTest {
    private val liveUrl: String? = System.getenv("FRIGATE_LIVE_URL")

    @Test
    fun `real server connects and its config and stats parse`() {
        assumeTrue("FRIGATE_LIVE_URL not set", !liveUrl.isNullOrBlank())
        SessionHarness(profile(local = liveUrl)).use { h ->
            val connected = h.await<ConnectionState.Connected>()
            assertTrue(connected.serverVersion?.startsWith("0.") == true)

            val result = runBlocking { h.session.execute { api -> api.config() to api.stats() } }
            assertTrue("fetch failed: $result", result is FrigateResult.Success)
            val (config, stats) = (result as FrigateResult.Success).value
            assertTrue(config.cameras.isNotEmpty())
            assertTrue(config.cameras.keys.all { it in stats.cameras.keys })
            assertTrue(config.cameras.values.all { it.live?.streams?.isNotEmpty() == true })
        }
    }
}
