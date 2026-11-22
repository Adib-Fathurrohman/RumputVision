package com.rumput.vision.domain.repository

import com.rumput.vision.domain.model.CameraWithStatus
import com.rumput.vision.domain.model.FrigateResult
import com.rumput.vision.domain.model.ServerStatus
import kotlinx.coroutines.flow.Flow

/** Cameras and server health for the active profile. */
interface ServerRepository {
    /** Cached cameras immediately, then live data after each successful [refresh]. */
    fun observeCameras(): Flow<List<CameraWithStatus>>

    /** Null until fetched in the current connection; never served from cache. */
    fun observeServerStatus(): Flow<ServerStatus?>

    /** Fetches config and stats once. Callers decide the cadence; nothing polls in the background. */
    suspend fun refresh(): FrigateResult<Unit>
}
