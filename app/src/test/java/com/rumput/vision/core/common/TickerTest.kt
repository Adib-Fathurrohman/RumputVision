package com.rumput.vision.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TickerTest {
    @Test
    fun `ticks only while active and continues counting after a pause`() = runTest {
        val active = MutableStateFlow(false)
        val ticks = mutableListOf<Long>()
        val job = launch { ticker(active, 1_000).collect { ticks += it } }

        advanceTimeBy(5_000)
        assertEquals(emptyList<Long>(), ticks)

        active.value = true
        runCurrent()
        advanceTimeBy(2_500)
        assertEquals(listOf(0L, 1L, 2L), ticks)

        active.value = false
        advanceTimeBy(10_000)
        assertEquals(3, ticks.size)

        active.value = true
        runCurrent()
        assertEquals(3L, ticks.last())
        job.cancel()
    }
}
