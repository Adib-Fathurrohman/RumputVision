package com.rumput.vision.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Non-secret profile metadata. Credentials are stored separately (see core/security). */
@Entity(tableName = "server_profiles")
data class ServerProfileEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "local_url") val localUrl: String?,
    @ColumnInfo(name = "remote_url") val remoteUrl: String?,
    @ColumnInfo(name = "auth_mode") val authMode: String,
    val username: String?,
    @ColumnInfo(name = "endpoint_preference") val endpointPreference: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    @ColumnInfo(name = "created_at") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMillis: Long,
)
