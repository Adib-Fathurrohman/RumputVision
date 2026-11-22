package com.rumput.vision.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ServerProfileEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class RumputDatabase : RoomDatabase() {
    abstract fun serverProfileDao(): ServerProfileDao
}
