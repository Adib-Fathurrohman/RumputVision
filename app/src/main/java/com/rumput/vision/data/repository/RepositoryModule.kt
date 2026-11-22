package com.rumput.vision.data.repository

import com.rumput.vision.domain.repository.AppPreferencesRepository
import com.rumput.vision.domain.repository.ServerProfileRepository
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
}
