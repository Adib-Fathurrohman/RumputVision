package com.rumput.vision.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CameraDao {
    @Query("SELECT * FROM cameras WHERE profile_id = :profileId ORDER BY ui_order ASC, name ASC")
    fun observeForProfile(profileId: String): Flow<List<CameraEntity>>

    @Query("SELECT * FROM cameras WHERE profile_id = :profileId")
    suspend fun getForProfile(profileId: String): List<CameraEntity>

    @Upsert
    suspend fun upsertAll(cameras: List<CameraEntity>)

    @Query("DELETE FROM cameras WHERE profile_id = :profileId AND name NOT IN (:keep)")
    suspend fun deleteOthers(profileId: String, keep: List<String>)

    @Query("DELETE FROM cameras WHERE profile_id = :profileId")
    suspend fun deleteForProfile(profileId: String)

    /** Makes the cache match the server: cameras removed from Frigate's config disappear here too. */
    @Transaction
    suspend fun replaceForProfile(profileId: String, cameras: List<CameraEntity>) {
        if (cameras.isEmpty()) {
            deleteForProfile(profileId)
        } else {
            deleteOthers(profileId, cameras.map { it.name })
            upsertAll(cameras)
        }
    }
}
