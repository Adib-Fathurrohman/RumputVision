package com.rumput.vision.feature.liveview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.rumput.vision.core.streaming.StreamState
import com.rumput.vision.domain.model.CameraStatus
import com.rumput.vision.domain.model.ConnectionRoute
import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.domain.model.StreamQuality
import com.rumput.vision.testing.FakeAppPreferencesRepository
import com.rumput.vision.testing.FakeCameraMedia
import com.rumput.vision.testing.FakeServerConnection
import com.rumput.vision.testing.FakeServerRepository
import com.rumput.vision.testing.FakeStreamPlayerFactory
import com.rumput.vision.testing.MainDispatcherRule
import com.rumput.vision.testing.cameraWithStatus
import com.rumput.vision.testing.url
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveViewModelsTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val server = FakeServerRepository()
    private val connection = FakeServerConnection(
        ConnectionState.Connected(ConnectionRoute.LOCAL, url("http://frigate.home.arpa"), "0.18.2", false),
    )
    private val media = FakeCameraMedia("http://frigate.home.arpa")
    private val prefs = FakeAppPreferencesRepository()
    private val players = FakeStreamPlayerFactory()

    private fun ws(stream: String) = "ws://frigate.home.arpa/live/mse/api/ws?src=$stream"

    @Test
    fun `live view plays the fullscreen quality, switches quality and camera, and releases its player`() = runTest {
        server.cameras.value = listOf(cameraWithStatus("garasi"), cameraWithStatus("halaman"), cameraWithStatus("off", CameraStatus.DISABLED))
        val store = ViewModelStore()
        val vm = LiveViewViewModel(SavedStateHandle(mapOf("cameraName" to "garasi")), server, connection, media, prefs, players)
        store.put("vm", vm)
        val player = players.created.single()

        assertEquals(ws("garasi_main"), player.source?.url) // default fullscreen quality is MAIN
        assertEquals(true, player.muted)
        vm.onStart()
        assertTrue(player.playing)

        vm.setQuality(StreamQuality.SUB)
        assertEquals(ws("garasi_sub"), player.source?.url)

        vm.toggleMute()
        assertEquals(false, player.muted)

        vm.switchCamera(+1)
        assertEquals("halaman", vm.uiState.value.cameraName)
        assertEquals(ws("halaman_sub"), player.source?.url)
        vm.switchCamera(+1) // the disabled camera is skipped, wrapping to the first
        assertEquals("garasi", vm.uiState.value.cameraName)

        vm.onStop()
        assertFalse(player.playing)
        store.clear()
        assertTrue(player.released)
        assertEquals("one player for the whole screen", 1, players.created.size)
        assertEquals(listOf(com.rumput.vision.core.streaming.BufferProfile.LOW_LATENCY), players.profiles)
    }

    @Test
    fun `live view suggests the sub stream only when main keeps stalling`() = runTest {
        server.cameras.value = listOf(cameraWithStatus("garasi"))
        val vm = LiveViewViewModel(SavedStateHandle(mapOf("cameraName" to "garasi")), server, connection, media, prefs, players)
        val player = players.created.single()
        assertFalse(vm.uiState.value.suggestSubStream)

        player.rebufferingFrequently.value = true
        assertTrue(vm.uiState.value.suggestSubStream)
        vm.setQuality(StreamQuality.SUB)
        assertFalse(vm.uiState.value.suggestSubStream)
    }

    @Test
    fun `no stream is prepared while disconnected`() = runTest {
        media.baseUrl = null
        server.cameras.value = listOf(cameraWithStatus("garasi"))
        LiveViewViewModel(SavedStateHandle(mapOf("cameraName" to "garasi")), server, connection, media, prefs, players)
        assertNull(players.created.single().source)
    }

    @Test
    fun `grid streams up to the limit on sub, muted, and shows stills for the rest`() = runTest {
        server.cameras.value = listOf("a", "b", "c", "d", "e").map { cameraWithStatus(it) }
        prefs.setMaxConcurrentGridStreams(2)
        val store = ViewModelStore()
        val vm = LiveGridViewModel(server, connection, media, prefs, players)
        store.put("vm", vm)
        vm.onStart()

        val state = vm.uiState.value
        assertEquals(listOf("a", "b", "c", "d"), state.tiles.map { it.camera.camera.name })
        assertEquals(listOf(0, 1, null, null), state.tiles.map { it.slot })
        assertEquals(2, state.pageCount)
        assertEquals(2, players.created.size)
        assertTrue(players.profiles.all { it == com.rumput.vision.core.streaming.BufferProfile.SMOOTH })
        assertEquals(listOf(ws("a_sub"), ws("b_sub")), players.created.map { it.source?.url })
        assertTrue(players.created.all { it.muted == true && it.playing })

        players.created[0].state.value = StreamState.Playing(704, 576)
        assertEquals(StreamState.Playing(704, 576), vm.uiState.value.tiles[0].stream)

        vm.nextPage()
        assertEquals(listOf("e"), vm.uiState.value.tiles.map { it.camera.camera.name })
        assertEquals(ws("e_sub"), players.created[0].source?.url)
        assertFalse("unused slot stops", players.created[1].playing)

        vm.onStop()
        assertTrue(players.created.none { it.playing })
        store.clear()
        assertTrue(players.created.all { it.released })
    }
}
