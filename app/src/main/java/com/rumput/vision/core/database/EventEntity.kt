package com.rumput.vision.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Recent event metadata, cached per profile so the event list works offline. No images are stored. */
@Entity(
    tableName = "events",
    primaryKeys = ["profile_id", "id"],
    foreignKeys = [
        ForeignKey(entity = ServerProfileEntity::class, parentColumns = ["id"], childColumns = ["profile_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("profile_id", "start_time")],
)
data class EventEntity(
    @ColumnInfo(name = "profile_id") val profileId: String,
    val id: String,
    val camera: String,
    val label: String,
    @ColumnInfo(name = "sub_label") val subLabel: String?,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long?,
    @ColumnInfo(name = "has_clip") val hasClip: Boolean,
    @ColumnInfo(name = "has_snapshot") val hasSnapshot: Boolean,
    /** Comma-separated. */
    val zones: String,
    val score: Double?,
    val retained: Boolean,
)

@Dao
interface EventDao {
    /**
     * [cameras]/[labels] are only applied when the matching flag is set (Room cannot bind empty lists
     * meaningfully).
     */
    @Query(
        """
        SELECT * FROM events WHERE profile_id = :profileId
          AND (:allCameras OR camera IN (:cameras))
          AND (:allLabels OR label IN (:labels))
          AND start_time >= :after AND start_time < :before
        ORDER BY start_time DESC LIMIT :limit
        """,
    )
    fun observe(
        profileId: String,
        allCameras: Boolean,
        cameras: List<String>,
        allLabels: Boolean,
        labels: List<String>,
        after: Long,
        before: Long,
        limit: Int,
    ): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE profile_id = :profileId AND id = :id")
    suspend fun get(profileId: String, id: String): EventEntity?

    @Upsert
    suspend fun upsertAll(events: List<EventEntity>)

    @Query(
        """
        DELETE FROM events WHERE profile_id = :profileId AND id NOT IN
          (SELECT id FROM events WHERE profile_id = :profileId ORDER BY start_time DESC LIMIT :keep)
        """,
    )
    suspend fun trim(profileId: String, keep: Int)

    @Transaction
    suspend fun store(profileId: String, events: List<EventEntity>, keep: Int) {
        upsertAll(events)
        trim(profileId, keep)
    }
}
