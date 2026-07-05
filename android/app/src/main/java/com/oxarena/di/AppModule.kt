package com.oxarena.di

import com.oxarena.BuildConfig
import com.oxarena.data.remote.SocketMultiplayerClient
import com.oxarena.domain.repository.MultiplayerClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides configuration values (the backend URL from BuildConfig).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @BackendUrl
    fun provideBackendUrl(): String = BuildConfig.BACKEND_URL
}

/**
 * Binds domain interfaces to their concrete data-layer implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    @Singleton
    abstract fun bindMultiplayerClient(impl: SocketMultiplayerClient): MultiplayerClient
}
