package com.rumput.vision.data.repository

import com.rumput.vision.core.datastore.AppPreferencesDataSource
import com.rumput.vision.domain.model.AppPreferences
import com.rumput.vision.domain.model.StreamQuality
import com.rumput.vision.domain.repository.AppPreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAppPreferencesRepository @Inject constructor(
    private val dataSource: AppPreferencesDataSource,
) : AppPreferencesRepository {
    override val preferences: Flow<AppPreferences> = dataSource.preferences

    override suspend fun setGridStreamQuality(quality: StreamQuality) = dataSource.setGridStreamQuality(quality)

    override suspend fun setFullscreenStreamQuality(quality: StreamQuality) =
        dataSource.setFullscreenStreamQuality(quality)

    override suspend fun setMaxConcurrentGridStreams(count: Int) = dataSource.setMaxConcurrentGridStreams(count)

    override suspend fun setStartMuted(muted: Boolean) = dataSource.setStartMuted(muted)

    override suspend fun setInAppEventUpdates(enabled: Boolean) = dataSource.setInAppEventUpdates(enabled)

    override suspend fun setEventNotificationsEnabled(enabled: Boolean) =
        dataSource.setEventNotificationsEnabled(enabled)
}
