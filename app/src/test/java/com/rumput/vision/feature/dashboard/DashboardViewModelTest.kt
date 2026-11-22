package com.rumput.vision.feature.dashboard

import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.domain.model.UnauthorizedReason
import com.rumput.vision.domain.repository.PasswordUpdate
import com.rumput.vision.domain.repository.SignInResult
import com.rumput.vision.testing.FakeServerConnection
import com.rumput.vision.testing.FakeServerProfileRepository
import com.rumput.vision.testing.FakeServerRepository
import com.rumput.vision.testing.MainDispatcherRule
import com.rumput.vision.testing.profile
import com.rumput.vision.testing.url
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val profiles = FakeServerProfileRepository()
    private val connection = FakeServerConnection()
    private val server = FakeServerRepository()

    private val media = com.rumput.vision.testing.FakeCameraMedia()

    private fun viewModel() = DashboardViewModel(profiles, connection, server, media)

    private val connected = ConnectionState.Connected(
        route = com.rumput.vision.domain.model.ConnectionRoute.LOCAL,
        serverUrl = url("http://frigate.home.arpa"),
        serverVersion = "0.18.2",
        anonymousAdmin = false,
    )

    @Test
    fun `refreshes every 30 seconds only while observed and connected`() = runTest(mainDispatcherRule.dispatcher) {
        profiles.saveProfile(profile(active = true), PasswordUpdate.Keep)
        val vm = viewModel()
        val collector = launch { vm.uiState.collect {} }
        runCurrent()
        assertEquals("no polling before connecting", 0, server.refreshCount)

        connection.state.value = connected
        runCurrent()
        assertEquals(1, server.refreshCount)
        advanceTimeBy(30_001)
        assertEquals(2, server.refreshCount)

        connection.state.value = ConnectionState.Unreachable(com.rumput.vision.domain.model.FrigateError.Unauthorized)
        advanceTimeBy(120_000)
        assertEquals("polling stops when disconnected", 2, server.refreshCount)

        connection.state.value = connected
        runCurrent()
        assertEquals(3, server.refreshCount)

        collector.cancel()
        advanceTimeBy(120_000)
        assertEquals("polling stops when the screen is gone", 3, server.refreshCount)
    }

    @Test
    fun `sign in passes the password through and exposes the result`() = runTest(mainDispatcherRule.dispatcher) {
        profiles.saveProfile(profile(active = true), PasswordUpdate.Keep)
        connection.state.value = ConnectionState.Unauthorized(UnauthorizedReason.SIGN_IN_REQUIRED)
        connection.signInResult = SignInResult.InvalidCredentials
        val vm = viewModel()
        val collector = launch { vm.uiState.collect {} }

        vm.signIn("hunter2", remember = false)
        runCurrent()

        assertEquals(listOf("hunter2" to false), connection.signIns)
        assertEquals(SignInResult.InvalidCredentials, vm.uiState.value.signIn.result)
        collector.cancel()
    }

    @Test
    fun `preview frames refresh every 10 s only while connected`() = runTest(mainDispatcherRule.dispatcher) {
        profiles.saveProfile(profile(active = true), PasswordUpdate.Keep)
        server.cameras.value = listOf(com.rumput.vision.testing.cameraWithStatus("garasi"))
        val vm = viewModel()
        val collector = launch { vm.uiState.collect {} }
        runCurrent()
        assertEquals(-1L, vm.uiState.value.snapshotTick)
        assertEquals(null, vm.uiState.value.snapshotUrls["garasi"])

        media.baseUrl = "http://frigate.home.arpa"
        connection.state.value = connected
        runCurrent()
        assertEquals(0L, vm.uiState.value.snapshotTick)
        assertEquals("http://frigate.home.arpa/api/garasi/latest.webp?height=360", vm.uiState.value.snapshotUrls["garasi"])

        advanceTimeBy(20_001)
        assertEquals(2L, vm.uiState.value.snapshotTick)
        collector.cancel()
    }
}
