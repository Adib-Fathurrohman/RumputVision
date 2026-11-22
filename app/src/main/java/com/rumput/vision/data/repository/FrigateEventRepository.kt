package com.rumput.vision.data.repository

import com.rumput.vision.core.database.EventDao
import com.rumput.vision.data.mapper.toDomain
import com.rumput.vision.data.mapper.toEntity
import com.rumput.vision.data.remote.FrigateRealtime
import com.rumput.vision.data.remote.FrigateSession
import com.rumput.vision.data.remote.RealtimeMessage
import com.rumput.vision.domain.model.Event
import com.rumput.vision.domain.model.EventFilter
import com.rumput.vision.domain.model.EventPage
import com.rumput.vision.domain.model.FrigateError
import com.rumput.vision.domain.model.FrigateResult
import com.rumput.vision.domain.model.RecordingDay
import com.rumput.vision.domain.model.RecordingSegment
import com.rumput.vision.domain.model.map
import com.rumput.vision.domain.repository.EventRepository
import com.rumput.vision.domain.repository.RecordingRepository
import com.rumput.vision.domain.repository.ServerProfileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.TimeZone
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import com.rumput.vision.domain.repository.AppPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FrigateEventRepository @Inject constructor(
    private val session: FrigateSession,
    private val profiles: ServerProfileRepository,
    private val eventDao: EventDao,
    realtime: FrigateRealtime,
    preferences: AppPreferencesRepository,
) : EventRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCachedEvents(filter: EventFilter, limit: Int): Flow<List<Event>> =
        profiles.observeActiveProfile().flatMapLatest { profile ->
            if (profile == null) {
                flowOf(emptyList())
            } else {
                eventDao.observe(
                    profileId = profile.id,
                    allCameras = filter.cameras.isEmpty(),
                    cameras = filter.cameras.toList(),
                    allLabels = filter.labels.isEmpty(),
                    labels = filter.labels.toList(),
                    after = filter.afterEpochMillis ?: Long.MIN_VALUE,
                    before = filter.beforeEpochMillis ?: Long.MAX_VALUE,
                    limit = limit,
                ).map { list -> list.map { it.toDomain() } }
            }
        }

    override suspend fun fetchEvents(filter: EventFilter, beforeEpochMillis: Long?, limit: Int): FrigateResult<EventPage> {
        val profile = profiles.observeActiveProfile().first() ?: return FrigateResult.Failure(FrigateError.NotConfigured)
        val before = listOfNotNull(beforeEpochMillis, filter.beforeEpochMillis).minOrNull()
        val result = session.execute { api ->
            api.events(
                cameras = filter.cameras.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(","),
                labels = filter.labels.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(","),
                after = filter.afterEpochMillis?.let { it / 1000.0 },
                before = before?.let { it / 1000.0 },
                limit = limit,
            ).map { it.toDomain() }
        }
        if (result is FrigateResult.Success && result.value.isNotEmpty()) {
            eventDao.store(profile.id, result.value.map { it.toEntity(profile.id) }, keep = CACHE_SIZE)
        }
        return result.map { events ->
            EventPage(events, nextBeforeEpochMillis = events.lastOrNull()?.startEpochMillis?.takeIf { events.size >= limit })
        }
    }

    override suspend fun getEvent(id: String): FrigateResult<Event> {
        val profile = profiles.observeActiveProfile().first() ?: return FrigateResult.Failure(FrigateError.NotConfigured)
        val remote = session.execute { api -> api.event(id).toDomain() }
        if (remote is FrigateResult.Success) {
            eventDao.store(profile.id, listOf(remote.value.toEntity(profile.id)), keep = CACHE_SIZE)
            return remote
        }
        return eventDao.get(profile.id, id)?.let { FrigateResult.Success(it.toDomain()) } ?: remote
    }

    override suspend fun labels(): FrigateResult<List<String>> = session.execute { api -> api.labels().sorted() }

    private val seen = object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>) = size > SEEN_SIZE
    }

    /** Off in Settings ("In-app event updates") means no `/ws` socket at all, not just ignored messages. */
    @OptIn(FlowPreview::class)
    override val eventActivity: Flow<Unit> = whileEnabled(
        preferences.preferences.map { it.inAppEventUpdates },
        realtime.messages
            .mapNotNull { activityKey(it) }
            // The same event/review is often announced several times (new, update, end); act once per change.
            .mapNotNull { key -> synchronized(seen) { key.takeIf { seen.put(it, true) == null } } }
            .debounce(ACTIVITY_DEBOUNCE_MILLIS)
            .map { },
    )

    private companion object {
        const val CACHE_SIZE = 300
        const val SEEN_SIZE = 500
        const val ACTIVITY_DEBOUNCE_MILLIS = 1_000L
    }
}

private val activityJson = Json { ignoreUnknownKeys = true }

/** Collects [source] only while [enabled] is true; switching off cancels the collection. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> whileEnabled(enabled: Flow<Boolean>, source: Flow<T>): Flow<T> =
    enabled.distinctUntilChanged().flatMapLatest { on -> if (on) source else emptyFlow() }

/** `topic:id:type` for event/review messages, else null. Payloads are hints, never trusted as data. */
internal fun activityKey(message: RealtimeMessage): String? {
    if (message.topic != "events" && message.topic != "reviews") return null
    val obj = runCatching { activityJson.parseToJsonElement(message.payload).jsonObject }.getOrNull()
        ?: return "${message.topic}:${message.payload.hashCode()}"
    val type = obj["type"]?.jsonPrimitive?.content ?: "?"
    val id = (obj["after"] as? JsonObject ?: obj["before"] as? JsonObject)?.get("id")?.jsonPrimitive?.content ?: "?"
    return "${message.topic}:$id:$type"
}

@Singleton
class FrigateRecordingRepository @Inject constructor(
    private val session: FrigateSession,
) : RecordingRepository {
    override suspend fun recordingDays(camera: String): FrigateResult<List<RecordingDay>> = session.execute { api ->
        // Frigate buckets days and hours in the requested zone; use the device's so dates match what users see.
        api.recordingDays(camera, TimeZone.getDefault().id).map { it.toDomain() }.sortedByDescending { it.date }
    }

    override suspend fun segments(camera: String, startEpochMillis: Long, endEpochMillis: Long): FrigateResult<List<RecordingSegment>> =
        session.execute { api ->
            api.recordingSegments(camera, startEpochMillis / 1000, (endEpochMillis + 999) / 1000)
                .map { it.toDomain() }
                .sortedBy { it.startEpochMillis }
        }
}
