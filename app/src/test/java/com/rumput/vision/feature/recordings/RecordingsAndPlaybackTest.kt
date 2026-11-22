package com.rumput.vision.feature.recordings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.rumput.vision.core.streaming.PlaybackState
import com.rumput.vision.domain.model.RecordingDay
import com.rumput.vision.domain.model.RecordingHour
import com.rumput.vision.domain.model.RecordingSegment
import com.rumput.vision.feature.playback.PlaybackViewModel
import com.rumput.vision.testing.FakeCameraMedia
import com.rumput.vision.testing.FakeRecordingPlayerFactory
import com.rumput.vision.testing.FakeRecordingRepository
import com.rumput.vision.testing.FakeServerRepository
import com.rumput.vision.testing.MainDispatcherRule
import com.rumput.vision.testing.cameraWithStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingsAndPlaybackTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val server = FakeServerRepository()
    private val recordings = FakeRecordingRepository()
    private val jakarta = ZoneId.of("Asia/Jakarta")

    @Test
    fun `selects the first camera and newest day, and builds local hour windows`() = runTest(mainDispatcherRule.dispatcher) {
        server.cameras.value = listOf(cameraWithStatus("garasi"), cameraWithStatus("halaman"))
        recordings.days = listOf(
            RecordingDay("2026-11-22", 7, listOf(RecordingHour(3, 276.0, 6), RecordingHour(4, 51.0, 1))),
            RecordingDay("2026-11-16", 11, emptyList()),
        )
        val vm = RecordingsViewModel(server, recordings)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("garasi", state.selectedCamera)
        assertEquals("2026-11-22", state.selectedDay)
        assertEquals(listOf("garasi"), recordings.dayRequests)

        // 2026-11-22 04:00 WIB (UTC+7) = 2026-11-21 21:00 UTC
        val window = vm.hourWindow("2026-11-22", 4, jakarta)!!
        assertEquals(java.time.ZonedDateTime.of(2026, 11, 22, 4, 0, 0, 0, jakarta).toEpochSecond(), window.startEpochSeconds)
        assertEquals(1_795_294_800L, window.startEpochSeconds)
        assertEquals(window.startEpochSeconds + 3600, window.endEpochSeconds)
        assertEquals(86_400L, vm.dayWindow("2026-11-22", jakarta)!!.let { it.endEpochSeconds - it.startEpochSeconds })

        vm.selectCamera("halaman"); advanceUntilIdle()
        assertEquals(listOf("garasi", "halaman"), recordings.dayRequests)
        job.cancel()
    }

    @Test
    fun `playback loads the VOD playlist, maps position to wall clock, and releases the player`() = runTest(mainDispatcherRule.dispatcher) {
        recordings.segments = listOf(RecordingSegment(1_000_000, 1_012_000), RecordingSegment(1_100_000, 1_110_000))
        val players = FakeRecordingPlayerFactory()
        val store = ViewModelStore()
        val vm = PlaybackViewModel(
            SavedStateHandle(mapOf("camera" to "garasi", "startEpochSeconds" to 1_000L, "endEpochSeconds" to 4_600L)),
            players, recordings, server, FakeCameraMedia("http://f"),
        )
        store.put("vm", vm)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val player = players.created.single()

        assertEquals("http://f/vod/garasi/start/1000/end/4600/index.m3u8", player.loaded)
        player.state.value = PlaybackState.Ready(playing = false, durationMillis = 22_000, buffering = false)
        player.positionMillis = 15_000
        player.state.value = PlaybackState.Ready(playing = true, durationMillis = 22_000, buffering = false)
        // The position ticker runs for as long as playback does, so advance a bounded amount of time.
        advanceTimeBy(600)
        assertEquals("15 s in is 3 s into the second recording", 1_103_000L, vm.uiState.value.wallClockMillis)

        vm.seekBy(-20_000)
        assertEquals(0L, player.positionMillis)
        vm.togglePlay()
        assertTrue((player.state.value as PlaybackState.Ready).playing.not())

        job.cancel()
        store.clear()
        assertTrue(player.released)
    }
}
