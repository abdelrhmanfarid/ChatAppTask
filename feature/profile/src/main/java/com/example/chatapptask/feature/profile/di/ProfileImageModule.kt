package com.example.chatapptask.feature.profile.di

import android.content.Context
import com.example.chatapptask.feature.profile.presentation.ContentResolverProfileImageReader
import com.example.chatapptask.feature.profile.presentation.ProfileImageReader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProfileImageModule {
    @Provides
    @Singleton
    fun provideProfileImageReader(
        @ApplicationContext context: Context,
    ): ProfileImageReader = ContentResolverProfileImageReader(context.contentResolver)
}
