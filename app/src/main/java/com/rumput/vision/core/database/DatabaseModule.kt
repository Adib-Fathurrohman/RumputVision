package com.rumput.vision.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RumputDatabase =
        Room.databaseBuilder(context, RumputDatabase::class.java, "rumput_vision.db").build()

    @Provides
    fun provideServerProfileDao(db: RumputDatabase): ServerProfileDao = db.serverProfileDao()

    @Provides
    fun provideCameraDao(db: RumputDatabase): CameraDao = db.cameraDao()

    @Provides
    fun provideEventDao(db: RumputDatabase): EventDao = db.eventDao()
}
