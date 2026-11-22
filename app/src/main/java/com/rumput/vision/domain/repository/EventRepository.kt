package com.rumput.vision.domain.repository

import com.rumput.vision.domain.model.Event
import com.rumput.vision.domain.model.EventFilter
import com.rumput.vision.domain.model.EventPage
import com.rumput.vision.domain.model.FrigateResult
import com.rumput.vision.domain.model.RecordingDay
import com.rumput.vision.domain.model.RecordingSegment
import kotlinx.coroutines.flow.Flow

interface EventRepository {
    /** Locally cached events matching [filter], newest first (for offline display). */
    fun observeCachedEvents(filter: EventFilter, limit: Int): Flow<List<Event>>

    /** One page from the server. The first page (no [beforeEpochMillis]) also refreshes the cache. */
    suspend fun fetchEvents(filter: EventFilter, beforeEpochMillis: Long?, limit: Int): FrigateResult<EventPage>

    /** Cached copy first, then the server. */
    suspend fun getEvent(id: String): FrigateResult<Event>

    suspend fun labels(): FrigateResult<List<String>>

    /**
     * Emits whenever the server reports event or review activity (realtime `/ws`), so screens can
     * refresh. De-duplicated and only active while collected and connected.
     */
    val eventActivity: Flow<Unit>
}

interface RecordingRepository {
    /** Days with recordings for [camera], newest first, bucketed in the device time zone. */
    suspend fun recordingDays(camera: String): FrigateResult<List<RecordingDay>>

    suspend fun segments(camera: String, startEpochMillis: Long, endEpochMillis: Long): FrigateResult<List<RecordingSegment>>
}
