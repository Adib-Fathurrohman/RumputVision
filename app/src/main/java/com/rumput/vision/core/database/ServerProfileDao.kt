package com.rumput.vision.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerProfileDao {
    @Query("SELECT * FROM server_profiles ORDER BY created_at ASC")
    fun observeAll(): Flow<List<ServerProfileEntity>>

    @Query("SELECT * FROM server_profiles WHERE is_active = 1 LIMIT 1")
    fun observeActive(): Flow<ServerProfileEntity?>

    @Query("SELECT * FROM server_profiles WHERE id = :id")
    suspend fun getById(id: String): ServerProfileEntity?

    @Query("SELECT COUNT(*) FROM server_profiles WHERE is_active = 1")
    suspend fun activeCount(): Int

    @Upsert
    suspend fun upsert(entity: ServerProfileEntity)

    @Query("DELETE FROM server_profiles WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE server_profiles SET is_active = (id = :id)")
    suspend fun setActive(id: String)

    @Query("SELECT id FROM server_profiles ORDER BY created_at ASC LIMIT 1")
    suspend fun firstId(): String?

    /** Saves [entity], making it active if no profile is active yet. */
    @Transaction
    suspend fun upsertEnsuringActive(entity: ServerProfileEntity) {
        upsert(entity)
        if (activeCount() == 0) setActive(entity.id)
    }

    /** Deletes the profile, then activates the oldest remaining profile if none is active. */
    @Transaction
    suspend fun deleteEnsuringActive(id: String) {
        deleteById(id)
        if (activeCount() == 0) firstId()?.let { setActive(it) }
    }
}
