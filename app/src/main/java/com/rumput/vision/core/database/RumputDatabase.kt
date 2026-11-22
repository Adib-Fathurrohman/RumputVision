package com.rumput.vision.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ServerProfileEntity::class, CameraEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class RumputDatabase : RoomDatabase() {
    abstract fun serverProfileDao(): ServerProfileDao

    abstract fun cameraDao(): CameraDao
}
