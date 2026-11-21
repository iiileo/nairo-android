package com.nairo.app.data.di

import com.nairo.app.data.DefaultNotificationRepository
import com.nairo.app.data.NotificationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {
    @Binds
    @Singleton
    fun bindNotificationRepository(
        impl: DefaultNotificationRepository,
    ): NotificationRepository
}
