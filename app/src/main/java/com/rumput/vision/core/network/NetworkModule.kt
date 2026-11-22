package com.rumput.vision.core.network

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {
    @Binds
    abstract fun bindNetworkMonitor(impl: ConnectivityNetworkMonitor): NetworkMonitor

    @Binds
    abstract fun bindForegroundMonitor(impl: ProcessForegroundMonitor): ForegroundMonitor

    companion object {
        @Provides
        @Singleton
        fun provideBaseOkHttpClient(): OkHttpClient = baseOkHttpClient()
    }
}

/**
 * Shared base client. Redirects are not followed: a redirect could silently move a request (and its
 * credentials) to another scheme or host. Connection tests report redirects to the user instead.
 */
fun baseOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
    .followRedirects(false)
    .followSslRedirects(false)
    .addInterceptor(TransportGuardInterceptor())
    .addNetworkInterceptor(DebugLoggingInterceptor())
    .build()
