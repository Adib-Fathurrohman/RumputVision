package com.rumput.vision.domain.model

data class AppPreferences(
    val gridStreamQuality: StreamQuality = StreamQuality.SUB,
    val fullscreenStreamQuality: StreamQuality = StreamQuality.MAIN,
    val maxConcurrentGridStreams: Int = DEFAULT_MAX_GRID_STREAMS,
    val startMuted: Boolean = true,
    val inAppEventUpdates: Boolean = true,
    val eventNotificationsEnabled: Boolean = false,
) {
    companion object {
        const val MIN_GRID_STREAMS = 1
        const val MAX_GRID_STREAMS = 4
        const val DEFAULT_MAX_GRID_STREAMS = 4
    }
}

enum class StreamQuality {
    /** Camera substream: lower resolution and bandwidth. */
    SUB,

    /** Camera main stream: full resolution. */
    MAIN,
}
