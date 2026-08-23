package com.example.chatapptask.data.chat.di

import com.example.chatapptask.data.chat.local.ChatLocalDataSource
import com.example.chatapptask.data.chat.local.RoomChatLocalDataSource
import com.example.chatapptask.data.chat.remote.ChatRemoteDataSource
import com.example.chatapptask.data.chat.remote.SupabaseChatRemoteDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ChatDataSourceModule {
    @Binds
    abstract fun bindChatLocalDataSource(
        implementation: RoomChatLocalDataSource,
    ): ChatLocalDataSource

    @Binds
    abstract fun bindChatRemoteDataSource(
        implementation: SupabaseChatRemoteDataSource,
    ): ChatRemoteDataSource
}
