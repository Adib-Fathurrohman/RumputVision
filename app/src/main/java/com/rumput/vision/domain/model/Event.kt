package com.rumput.vision.domain.model

/** A Frigate event (tracked object, or an event created through the API, e.g. by Home Assistant). */
data class Event(
    val id: String,
    val camera: String,
    val label: String,
    val subLabel: String?,
    val startEpochMillis: Long,
    /** Null while the event is in progress. */
    val endEpochMillis: Long?,
    val hasClip: Boolean,
    val hasSnapshot: Boolean,
    val zones: List<String>,
    val score: Double?,
    val retained: Boolean,
) {
    val inProgress: Boolean get() = endEpochMillis == null
    val durationMillis: Long? get() = endEpochMillis?.let { it - startEpochMillis }
}

/** `orang_terdeteksi` → "Orang terdeteksi". Labels come from the server and are not translated. */
fun labelDisplayName(label: String): String =
    label.replace('_', ' ').trim().replaceFirstChar { it.titlecase() }.ifEmpty { label }

/** Empty sets mean "all". Times are epoch millis; null means open-ended. */
data class EventFilter(
    val cameras: Set<String> = emptySet(),
    val labels: Set<String> = emptySet(),
    val afterEpochMillis: Long? = null,
    val beforeEpochMillis: Long? = null,
) {
    fun matches(event: Event): Boolean =
        (cameras.isEmpty() || event.camera in cameras) &&
            (labels.isEmpty() || event.label in labels) &&
            (afterEpochMillis == null || event.startEpochMillis >= afterEpochMillis) &&
            (beforeEpochMillis == null || event.startEpochMillis < beforeEpochMillis)
}

/** One page of events, newest first. [nextBefore] continues the list; null when the end was reached. */
data class EventPage(val events: List<Event>, val nextBeforeEpochMillis: Long?)

data class RecordingDay(
    /** Local date as reported by Frigate in the requested time zone, ISO `yyyy-MM-dd`. */
    val date: String,
    val events: Int,
    val hours: List<RecordingHour>,
)

data class RecordingHour(
    /** 0–23, local time. */
    val hour: Int,
    val recordedSeconds: Double,
    val events: Int,
)

/** A stored recording segment, in wall-clock epoch millis. */
data class RecordingSegment(val startEpochMillis: Long, val endEpochMillis: Long)
