package com.rumput.vision.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "cameras",
    primaryKeys = ["profile_id", "name"],
    foreignKeys = [
        ForeignKey(
            entity = ServerProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profile_id")],
)
data class CameraEntity(
    @ColumnInfo(name = "profile_id") val profileId: String,
    val name: String,
    val enabled: Boolean,
    @ColumnInfo(name = "ui_order") val uiOrder: Int,
    @ColumnInfo(name = "show_on_dashboard") val showOnDashboard: Boolean,
    /** JSON list of {label, stream}. */
    @ColumnInfo(name = "live_streams") val liveStreams: String,
    @ColumnInfo(name = "detect_width") val detectWidth: Int?,
    @ColumnInfo(name = "detect_height") val detectHeight: Int?,
    @ColumnInfo(name = "detect_enabled") val detectEnabled: Boolean,
    @ColumnInfo(name = "record_enabled") val recordEnabled: Boolean,
    @ColumnInfo(name = "snapshots_enabled") val snapshotsEnabled: Boolean,
    @ColumnInfo(name = "audio_enabled") val audioEnabled: Boolean,
    @ColumnInfo(name = "last_seen_online_at") val lastSeenOnlineAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
