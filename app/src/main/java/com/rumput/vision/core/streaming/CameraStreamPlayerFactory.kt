package com.rumput.vision.core.streaming

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** Creates players. Call on the main thread; every player created must eventually be released. */
fun interface CameraStreamPlayerFactory {
    fun create(profile: BufferProfile): CameraStreamPlayer
}

@Singleton
class Media3CameraStreamPlayerFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clients: StreamClientProvider,
) : CameraStreamPlayerFactory {
    private val codecs: StreamCodecs by lazy { StreamCodecs.forDevice() }

    override fun create(profile: BufferProfile): CameraStreamPlayer = Media3CameraStreamPlayer(context, clients, codecs, profile)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class StreamingModule {
    @Binds
    abstract fun bindPlayerFactory(impl: Media3CameraStreamPlayerFactory): CameraStreamPlayerFactory
}
