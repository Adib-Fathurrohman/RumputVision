package com.rumput.vision.feature.cameras

import androidx.lifecycle.SavedStateHandle
import com.rumput.vision.domain.model.ConnectionRoute
import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.domain.model.FrigateError
import com.rumput.vision.domain.model.NetworkFailure
import com.rumput.vision.testing.FakeCameraMedia
import com.rumput.vision.testing.FakeServerConnection
import com.rumput.vision.testing.FakeServerRepository
import com.rumput.vision.testing.MainDispatcherRule
import com.rumput.vision.testing.cameraWithStatus
import com.rumput.vision.testing.url
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraDetailViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val server = FakeServerRepository()
    private val connection = FakeServerConnection()
    private val media = FakeCameraMedia()

    private fun viewModel(name: String = "garasi") =
        CameraDetailViewModel(SavedStateHandle(mapOf("cameraName" to name)), server, connection, media)

    private fun connect() {
        media.baseUrl = "http://frigate.home.arpa"
        connection.state.value = ConnectionState.Connected(ConnectionRoute.LOCAL, url("http://frigate.home.arpa"), "0.18.2", false)
    }

    @Test
    fun `shows the camera, refreshes the frame every 4 s and stats every 15 s while connected`() = runTest(mainDispatcherRule.dispatcher) {
        server.cameras.value = listOf(cameraWithStatus("garasi"), cameraWithStatus("halaman"))
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        connect()
        runCurrent()

        val state = vm.uiState.value
        assertEquals("garasi", state.camera?.camera?.name)
        assertEquals("http://frigate.home.arpa/api/garasi/latest.webp?height=720", state.snapshotUrl)
        assertEquals(0L, state.snapshotTick)
        assertEquals(1, server.refreshCount)

        advanceTimeBy(8_001)
        assertEquals(2L, vm.uiState.value.snapshotTick)
        advanceTimeBy(8_000)
        assertEquals(2, server.refreshCount)

        connection.state.value = ConnectionState.Unreachable(FrigateError.Unreachable(NetworkFailure.TIMEOUT))
        media.baseUrl = null
        val tickWhenLost = vm.uiState.value.snapshotTick
        advanceTimeBy(60_000)
        assertEquals("no frame refresh while unreachable", tickWhenLost, vm.uiState.value.snapshotTick)
        assertEquals("no stats polling while unreachable", 2, server.refreshCount)
        job.cancel()
    }

    @Test
    fun `unknown camera in a known list is not found`() = runTest(mainDispatcherRule.dispatcher) {
        server.cameras.value = listOf(cameraWithStatus("halaman"))
        val vm = viewModel("garasi")
        val job = launch { vm.uiState.collect {} }
        runCurrent()

        assertTrue(vm.uiState.value.notFound)
        assertNull(vm.uiState.value.camera)
        job.cancel()
    }

    @Test
    fun `empty list while connecting is loading, not "not found"`() = runTest(mainDispatcherRule.dispatcher) {
        connection.state.value = ConnectionState.Connecting
        val vm = viewModel("garasi")
        val job = launch { vm.uiState.collect {} }
        runCurrent()

        assertTrue(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.notFound)
        job.cancel()
    }
}
