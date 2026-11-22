package com.rumput.vision.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.rumput.vision.domain.model.AppPreferences
import com.rumput.vision.domain.model.StreamQuality
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppPreferencesDataSourceTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `defaults, updates and clamping`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tmp.newFile("prefs.preferences_pb").also { it.delete() }
        }
        val source = AppPreferencesDataSource(dataStore)

        assertEquals(AppPreferences(), source.preferences.first())

        source.setGridStreamQuality(StreamQuality.MAIN)
        source.setFullscreenStreamQuality(StreamQuality.SUB)
        source.setStartMuted(false)
        source.setInAppEventUpdates(false)
        source.setMaxConcurrentGridStreams(99)

        val prefs = source.preferences.first()
        assertEquals(StreamQuality.MAIN, prefs.gridStreamQuality)
        assertEquals(StreamQuality.SUB, prefs.fullscreenStreamQuality)
        assertEquals(false, prefs.startMuted)
        assertEquals(false, prefs.inAppEventUpdates)
        assertEquals(AppPreferences.MAX_GRID_STREAMS, prefs.maxConcurrentGridStreams)

        source.setMaxConcurrentGridStreams(0)
        assertEquals(AppPreferences.MIN_GRID_STREAMS, source.preferences.first().maxConcurrentGridStreams)
    }
}
