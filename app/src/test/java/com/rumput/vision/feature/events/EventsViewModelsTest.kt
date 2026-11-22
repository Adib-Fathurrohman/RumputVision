package com.rumput.vision.feature.events

import androidx.lifecycle.SavedStateHandle
import com.rumput.vision.core.common.Clock
import com.rumput.vision.domain.model.FrigateError
import com.rumput.vision.domain.model.NetworkFailure
import com.rumput.vision.testing.FakeCameraMedia
import com.rumput.vision.testing.FakeEventRepository
import com.rumput.vision.testing.FakeServerConnection
import com.rumput.vision.testing.FakeServerRepository
import com.rumput.vision.testing.MainDispatcherRule
import com.rumput.vision.testing.cameraWithStatus
import com.rumput.vision.testing.event
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventsViewModelsTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val repo = FakeEventRepository()
    private val server = FakeServerRepository()
    private val now = 10_000_000_000L

    private fun viewModel() = EventsViewModel(repo, server, FakeServerConnection(), FakeCameraMedia("http://f"), Clock { now })

    // 120 events, newest first, alternating cameras, one minute apart.
    private val all = (0 until 120).map { i -> event("e$i", camera = if (i % 2 == 0) "garasi" else "halaman", start = now - i * 60_000L) }

    @Test
    fun `loads the first page, then more pages on request until the end`() = runTest(mainDispatcherRule.dispatcher) {
        repo.remote = all
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(EventsViewModel.PAGE_SIZE, vm.uiState.value.events.size)
        assertFalse(vm.uiState.value.endReached)

        vm.loadMore(); advanceUntilIdle()
        vm.loadMore(); advanceUntilIdle()
        assertEquals(120, vm.uiState.value.events.size)
        assertTrue(vm.uiState.value.endReached)
        assertEquals("pages continue from the oldest loaded event", all[49].startEpochMillis, repo.requests[1].second)
        job.cancel()
    }

    @Test
    fun `camera and range filters are applied and can be cleared`() = runTest(mainDispatcherRule.dispatcher) {
        repo.remote = all
        server.cameras.value = listOf(cameraWithStatus("garasi"), cameraWithStatus("halaman"))
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.toggleCamera("garasi"); advanceUntilIdle()
        assertTrue(vm.uiState.value.events.all { it.camera == "garasi" })
        assertEquals(setOf("garasi"), repo.requests.last().first.cameras)

        vm.setRange(RangePreset.LAST_24_HOURS); advanceUntilIdle()
        assertEquals(now - 24 * 3600 * 1000L, repo.requests.last().first.afterEpochMillis)

        vm.clearFilters(); advanceUntilIdle()
        assertFalse(vm.uiState.value.filterActive)
        assertTrue(repo.requests.last().first.cameras.isEmpty())
        job.cancel()
    }

    @Test
    fun `offline shows cached events, labelled`() = runTest(mainDispatcherRule.dispatcher) {
        repo.cached.value = all.take(3)
        repo.failWith = FrigateError.Unreachable(NetworkFailure.TIMEOUT)
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(3, vm.uiState.value.events.size)
        assertTrue(vm.uiState.value.showingCached)
        job.cancel()
    }

    @Test
    fun `realtime activity adds new events on top without dropping loaded pages`() = runTest(mainDispatcherRule.dispatcher) {
        repo.remote = all
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.loadMore(); advanceUntilIdle()
        assertEquals(100, vm.uiState.value.events.size)

        repo.remote = listOf(event("new", start = now + 60_000)) + all
        repo.eventActivity.emit(Unit); advanceUntilIdle()

        assertEquals("new", vm.uiState.value.events.first().id)
        assertEquals(101, vm.uiState.value.events.size)
        job.cancel()
    }

    @Test
    fun `event detail loads the event and pads the clip window`() = runTest(mainDispatcherRule.dispatcher) {
        repo.remote = listOf(event("e1", start = 1_000_000, end = 1_035_000))
        val vm = EventDetailViewModel(SavedStateHandle(mapOf("eventId" to "e1")), repo, server, FakeCameraMedia("http://f"))
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val e = vm.uiState.value.event!!
        assertEquals("http://f/api/events/e1/snapshot.jpg?height=576", vm.uiState.value.snapshotUrl)
        assertEquals(ClipWindow("garasi", 995, 1040), vm.clipWindow(e))
        assertEquals(ClipWindow("garasi", 995, 2005), vm.clipWindow(e.copy(endEpochMillis = null), nowMillis = 2_000_000))
        job.cancel()
    }

    @Test
    fun `missing event shows an error`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = EventDetailViewModel(SavedStateHandle(mapOf("eventId" to "gone")), repo, server, FakeCameraMedia("http://f"))
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertNull(vm.uiState.value.event)
        assertEquals(FrigateError.Http(404), vm.uiState.value.error)
        job.cancel()
    }
}
