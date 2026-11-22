package com.rumput.vision.data.repository

import com.rumput.vision.data.remote.FrigateConnectionTester
import com.rumput.vision.data.remote.FrigateSession
import com.rumput.vision.domain.repository.AppPreferencesRepository
import com.rumput.vision.domain.repository.ConnectionTester
import com.rumput.vision.domain.repository.ServerConnection
import com.rumput.vision.domain.repository.ServerProfileRepository
import com.rumput.vision.domain.repository.ServerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindServerProfileRepository(impl: DefaultServerProfileRepository): ServerProfileRepository

    @Binds
    abstract fun bindAppPreferencesRepository(impl: DefaultAppPreferencesRepository): AppPreferencesRepository

    @Binds
    abstract fun bindServerConnection(impl: FrigateSession): ServerConnection

    @Binds
    abstract fun bindServerRepository(impl: FrigateServerRepository): ServerRepository

    @Binds
    abstract fun bindConnectionTester(impl: FrigateConnectionTester): ConnectionTester
}
