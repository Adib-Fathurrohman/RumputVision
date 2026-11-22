package com.rumput.vision.core.streaming

import com.rumput.vision.domain.model.RecordingSegment
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingTimelineTest {
    // Two recordings with a 10-minute gap; the requested window cuts the first one.
    private val timeline = RecordingTimeline(
        segments = listOf(RecordingSegment(1_000, 13_000), RecordingSegment(613_000, 625_000)),
        rangeStartMillis = 5_000,
        rangeEndMillis = 3_600_000,
    )

    @Test
    fun `playable length excludes gaps and the part before the window`() {
        assertEquals(8_000 + 12_000L, timeline.totalMillis)
        assertEquals(5_000L, timeline.segments.first().startEpochMillis)
    }

    @Test
    fun `positions map to wall clock across the gap`() {
        assertEquals(5_000L, timeline.wallClockAt(0))
        assertEquals(12_999L, timeline.wallClockAt(7_999))
        assertEquals(613_000L, timeline.wallClockAt(8_000))
        assertEquals(625_000L, timeline.wallClockAt(99_999))
    }

    @Test
    fun `wall clock maps back to positions, gaps jump to the next recording`() {
        assertEquals(0L, timeline.positionAt(0))
        assertEquals(3_000L, timeline.positionAt(8_000))
        assertEquals(8_000L, timeline.positionAt(300_000))
        assertEquals(10_000L, timeline.positionAt(615_000))
        assertEquals(20_000L, timeline.positionAt(9_999_999))
    }

    @Test
    fun `anchored on the real playlist length, matching Frigate 0_18_2 output`() {
        // Real case: window 0..45 s, segments of ~8/12 s; Frigate's playlist was 48.93 s:
        // 19.969 (the −4 s and +4 s segments) + 7.988 + 11.989 + 7.982 + 1.0 (trimmed at the end).
        val w = 1_000_000L
        val segments = listOf(-4_000L to 7_980L, 4_000L to 11_990L, 16_000L to 7_990L, 24_000L to 11_990L, 36_000L to 7_980L, 44_000L to 11_990L)
            .map { (offset, length) -> RecordingSegment(w + offset, w + offset + length) }
        val line = RecordingTimeline(segments, rangeStartMillis = w, rangeEndMillis = w + 45_000, playlistMillis = 48_930)

        assertEquals(48_930L, line.totalMillis)
        assertEquals("playback starts at the segment before the window", w - 4_000, line.wallClockAt(0))
        assertEquals(w + 7_020, line.wallClockAt(11_000))
        assertEquals("ends exactly at the requested end", w + 45_000, line.wallClockAt(48_930))
    }
}
