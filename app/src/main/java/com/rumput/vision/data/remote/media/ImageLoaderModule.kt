package com.rumput.vision.data.remote.media

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.rumput.vision.data.remote.FrigateSession
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {
    /**
     * Camera frames go through the session's authenticated client and are kept in memory only:
     * there is no disk cache, so no camera images are written to storage.
     */
    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context, session: FrigateSession): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { session.mediaCallFactory })) }
            .diskCache(null)
            .crossfade(true)
            .build()
}
