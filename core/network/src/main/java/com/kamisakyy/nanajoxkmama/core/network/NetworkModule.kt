package com.kamisakyy.nanajoxkmama.core.network

import com.kamisakyy.nanajoxkmama.core.common.AppDispatchers
import com.kamisakyy.nanajoxkmama.core.common.DefaultDispatchers
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {
    @Binds
    @Singleton
    abstract fun dispatchers(impl: DefaultDispatchers): AppDispatchers
}
