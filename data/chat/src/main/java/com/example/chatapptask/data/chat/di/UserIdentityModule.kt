package com.example.chatapptask.data.chat.di

import android.content.Context
import com.example.chatapptask.core.common.identity.DataStoreUserIdentityStore
import com.example.chatapptask.core.common.identity.UserIdentityStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UserIdentityModule {
    @Provides
    @Singleton
    fun provideUserIdentityStore(
        @ApplicationContext context: Context,
    ): UserIdentityStore = DataStoreUserIdentityStore(context)
}
