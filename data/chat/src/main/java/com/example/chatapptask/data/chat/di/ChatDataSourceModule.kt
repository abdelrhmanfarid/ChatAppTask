package com.example.chatapptask.data.chat.di

import com.example.chatapptask.data.chat.local.ChatLocalDataSource
import com.example.chatapptask.data.chat.local.FileOutgoingMediaStore
import com.example.chatapptask.data.chat.local.OutgoingMediaStore
import com.example.chatapptask.data.chat.local.RoomChatLocalDataSource
import com.example.chatapptask.data.chat.remote.ChatRemoteDataSource
import com.example.chatapptask.data.chat.remote.SupabaseChatRemoteDataSource
import com.example.chatapptask.data.chat.repository.DefaultChatRepository
import com.example.chatapptask.core.domain.repository.ChatRepository
import com.example.chatapptask.core.domain.repository.UserRepository
import com.example.chatapptask.data.chat.repository.DefaultUserRepository
import com.example.chatapptask.data.chat.worker.MediaMessageSendScheduler
import com.example.chatapptask.data.chat.worker.TextMessageSendScheduler
import com.example.chatapptask.data.chat.worker.WorkManagerMediaMessageSendScheduler
import com.example.chatapptask.data.chat.worker.WorkManagerTextMessageSendScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ChatDataSourceModule {
    @Binds
    abstract fun bindUserRepository(
        implementation: DefaultUserRepository,
    ): UserRepository

    @Binds
    abstract fun bindChatRepository(
        implementation: DefaultChatRepository,
    ): ChatRepository

    @Binds
    abstract fun bindChatLocalDataSource(
        implementation: RoomChatLocalDataSource,
    ): ChatLocalDataSource

    @Binds
    abstract fun bindOutgoingMediaStore(
        implementation: FileOutgoingMediaStore,
    ): OutgoingMediaStore

    @Binds
    abstract fun bindChatRemoteDataSource(
        implementation: SupabaseChatRemoteDataSource,
    ): ChatRemoteDataSource

    @Binds
    abstract fun bindTextMessageSendScheduler(
        implementation: WorkManagerTextMessageSendScheduler,
    ): TextMessageSendScheduler

    @Binds
    abstract fun bindMediaMessageSendScheduler(
        implementation: WorkManagerMediaMessageSendScheduler,
    ): MediaMessageSendScheduler
}
