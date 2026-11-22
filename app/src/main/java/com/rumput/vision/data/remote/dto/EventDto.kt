package com.rumput.vision.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** `GET /api/events` item (`EventResponse`, Frigate 0.18; verified against the live server). */
@Serializable
data class EventDto(
    val id: String,
    val camera: String,
    val label: String,
    /** A string, or `[name, score]` in some versions: decoded loosely. */
    @SerialName("sub_label") val subLabel: JsonElement? = null,
    @SerialName("start_time") val startTime: Double,
    /** Null while the event is in progress. */
    @SerialName("end_time") val endTime: Double? = null,
    @SerialName("has_clip") val hasClip: Boolean = false,
    @SerialName("has_snapshot") val hasSnapshot: Boolean = false,
    val zones: List<String> = emptyList(),
    @SerialName("retain_indefinitely") val retainIndefinitely: Boolean = false,
    @SerialName("top_score") val topScore: Double? = null,
    val data: JsonObject? = null,
)

/** `GET /api/{camera}/recordings/summary?timezone=` item. */
@Serializable
data class RecordingDayDto(
    val day: String,
    val events: Int = 0,
    val hours: List<RecordingHourDto> = emptyList(),
)

@Serializable
data class RecordingHourDto(
    /** Two-digit local hour, e.g. "07". */
    val hour: String,
    val events: Int = 0,
    val motion: Int = 0,
    val objects: Int = 0,
    /** Recorded seconds in this hour. */
    val duration: Double = 0.0,
)

/** `GET /api/{camera}/recordings?after=&before=` item. */
@Serializable
data class RecordingSegmentDto(
    val id: String,
    @SerialName("start_time") val startTime: Double,
    @SerialName("end_time") val endTime: Double,
    val duration: Double = 0.0,
    val motion: Int = 0,
    val objects: Int = 0,
)
