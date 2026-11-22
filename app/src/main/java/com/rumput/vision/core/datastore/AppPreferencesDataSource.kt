package com.rumput.vision.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rumput.vision.domain.model.AppPreferences
import com.rumput.vision.domain.model.StreamQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Non-sensitive preferences only. Secrets never go here. */
@Singleton
class AppPreferencesDataSource @Inject constructor(
    @param:AppPreferencesStore private val dataStore: DataStore<Preferences>,
) {
    val preferences: Flow<AppPreferences> = dataStore.data.map { prefs ->
        val defaults = AppPreferences()
        AppPreferences(
            gridStreamQuality = prefs[Keys.GRID_QUALITY].toQuality(defaults.gridStreamQuality),
            fullscreenStreamQuality = prefs[Keys.FULLSCREEN_QUALITY].toQuality(defaults.fullscreenStreamQuality),
            maxConcurrentGridStreams = (prefs[Keys.MAX_GRID_STREAMS] ?: defaults.maxConcurrentGridStreams)
                .coerceIn(AppPreferences.MIN_GRID_STREAMS, AppPreferences.MAX_GRID_STREAMS),
            startMuted = prefs[Keys.START_MUTED] ?: defaults.startMuted,
            inAppEventUpdates = prefs[Keys.IN_APP_EVENT_UPDATES] ?: defaults.inAppEventUpdates,
            eventNotificationsEnabled = prefs[Keys.EVENT_NOTIFICATIONS] ?: defaults.eventNotificationsEnabled,
        )
    }

    suspend fun setGridStreamQuality(quality: StreamQuality) = set(Keys.GRID_QUALITY, quality.name)

    suspend fun setFullscreenStreamQuality(quality: StreamQuality) = set(Keys.FULLSCREEN_QUALITY, quality.name)

    suspend fun setMaxConcurrentGridStreams(count: Int) =
        set(Keys.MAX_GRID_STREAMS, count.coerceIn(AppPreferences.MIN_GRID_STREAMS, AppPreferences.MAX_GRID_STREAMS))

    suspend fun setStartMuted(muted: Boolean) = set(Keys.START_MUTED, muted)

    suspend fun setInAppEventUpdates(enabled: Boolean) = set(Keys.IN_APP_EVENT_UPDATES, enabled)

    suspend fun setEventNotificationsEnabled(enabled: Boolean) = set(Keys.EVENT_NOTIFICATIONS, enabled)

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private fun String?.toQuality(default: StreamQuality): StreamQuality =
        StreamQuality.entries.firstOrNull { it.name == this } ?: default

    private object Keys {
        val GRID_QUALITY = stringPreferencesKey("stream.grid_quality")
        val FULLSCREEN_QUALITY = stringPreferencesKey("stream.fullscreen_quality")
        val MAX_GRID_STREAMS = intPreferencesKey("stream.max_grid_streams")
        val START_MUTED = booleanPreferencesKey("stream.start_muted")
        val IN_APP_EVENT_UPDATES = booleanPreferencesKey("events.in_app_updates")
        val EVENT_NOTIFICATIONS = booleanPreferencesKey("notifications.events_enabled")
    }
}
