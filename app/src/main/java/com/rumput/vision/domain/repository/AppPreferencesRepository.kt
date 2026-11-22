package com.rumput.vision.domain.repository

import com.rumput.vision.domain.model.AppPreferences
import com.rumput.vision.domain.model.StreamQuality
import kotlinx.coroutines.flow.Flow

interface AppPreferencesRepository {
    val preferences: Flow<AppPreferences>

    suspend fun setGridStreamQuality(quality: StreamQuality)

    suspend fun setFullscreenStreamQuality(quality: StreamQuality)

    /** Clamped to [AppPreferences.MIN_GRID_STREAMS]..[AppPreferences.MAX_GRID_STREAMS]. */
    suspend fun setMaxConcurrentGridStreams(count: Int)

    suspend fun setStartMuted(muted: Boolean)

    suspend fun setInAppEventUpdates(enabled: Boolean)

    suspend fun setEventNotificationsEnabled(enabled: Boolean)
}
