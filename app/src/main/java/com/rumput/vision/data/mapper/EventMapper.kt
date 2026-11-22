package com.rumput.vision.data.mapper

import com.rumput.vision.core.database.EventEntity
import com.rumput.vision.data.remote.dto.EventDto
import com.rumput.vision.data.remote.dto.RecordingDayDto
import com.rumput.vision.data.remote.dto.RecordingSegmentDto
import com.rumput.vision.domain.model.Event
import com.rumput.vision.domain.model.RecordingDay
import com.rumput.vision.domain.model.RecordingHour
import com.rumput.vision.domain.model.RecordingSegment
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

fun EventDto.toDomain(): Event = Event(
    id = id,
    camera = camera,
    label = label,
    subLabel = when (val s = subLabel) {
        is JsonPrimitive -> s.content.takeIf { s.isString && it.isNotBlank() }
        is JsonArray -> (s.firstOrNull() as? JsonPrimitive)?.content
        else -> null
    },
    startEpochMillis = (startTime * 1000).toLong(),
    endEpochMillis = endTime?.let { (it * 1000).toLong() },
    hasClip = hasClip,
    hasSnapshot = hasSnapshot,
    zones = zones,
    score = (topScore ?: data?.get("top_score")?.jsonPrimitive?.doubleOrNull)?.takeIf { it > 0 },
    retained = retainIndefinitely,
)

fun Event.toEntity(profileId: String) = EventEntity(
    profileId = profileId,
    id = id,
    camera = camera,
    label = label,
    subLabel = subLabel,
    startTime = startEpochMillis,
    endTime = endEpochMillis,
    hasClip = hasClip,
    hasSnapshot = hasSnapshot,
    zones = zones.joinToString(","),
    score = score,
    retained = retained,
)

fun EventEntity.toDomain() = Event(
    id = id,
    camera = camera,
    label = label,
    subLabel = subLabel,
    startEpochMillis = startTime,
    endEpochMillis = endTime,
    hasClip = hasClip,
    hasSnapshot = hasSnapshot,
    zones = zones.split(',').filter { it.isNotBlank() },
    score = score,
    retained = retained,
)

fun RecordingDayDto.toDomain() = RecordingDay(
    date = day,
    events = events,
    hours = hours.mapNotNull { h -> h.hour.toIntOrNull()?.let { RecordingHour(it, h.duration, h.events) } }.sortedBy { it.hour },
)

fun RecordingSegmentDto.toDomain() = RecordingSegment((startTime * 1000).toLong(), (endTime * 1000).toLong())
