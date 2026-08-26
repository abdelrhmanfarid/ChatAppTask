package com.example.chatapptask.data.chat.di

import com.example.chatapptask.core.domain.ChatMediaPublicUrlFactory
import com.example.chatapptask.core.domain.ProfileImagePublicUrlFactory
import com.example.chatapptask.data.chat.local.ChatLocalDataSource
import com.example.chatapptask.data.chat.local.FileOutgoingMediaStore
import com.example.chatapptask.data.chat.local.OutgoingMediaStore
import com.example.chatapptask.data.chat.local.RoomChatLocalDataSource
import com.example.chatapptask.data.chat.push.CurrentPushUserMatcher
import com.example.chatapptask.data.chat.push.PushInstallationIdStore
import com.example.chatapptask.data.chat.push.SharedPreferencesPushInstallationIdStore
import com.example.chatapptask.data.chat.push.UserIdentityCurrentPushUserMatcher
import com.example.chatapptask.data.chat.remote.ChatRemoteDataSource
import com.example.chatapptask.data.chat.remote.PushRegistrationRemoteDataSource
import com.example.chatapptask.data.chat.remote.SupabaseChatMediaPublicUrlFactory
import com.example.chatapptask.data.chat.remote.SupabaseChatRemoteDataSource
import com.example.chatapptask.data.chat.remote.SupabaseProfileImagePublicUrlFactory
import com.example.chatapptask.data.chat.remote.SupabasePushRegistrationRemoteDataSource
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
    abstract fun bindPushRegistrationRemoteDataSource(
        implementation: SupabasePushRegistrationRemoteDataSource,
    ): PushRegistrationRemoteDataSource

    @Binds
    abstract fun bindPushInstallationIdStore(
        implementation: SharedPreferencesPushInstallationIdStore,
    ): PushInstallationIdStore

    @Binds
    abstract fun bindCurrentPushUserMatcher(
        implementation: UserIdentityCurrentPushUserMatcher,
    ): CurrentPushUserMatcher

    @Binds
    abstract fun bindChatMediaPublicUrlFactory(
        implementation: SupabaseChatMediaPublicUrlFactory,
    ): ChatMediaPublicUrlFactory

    @Binds
    abstract fun bindProfileImagePublicUrlFactory(
        implementation: SupabaseProfileImagePublicUrlFactory,
    ): ProfileImagePublicUrlFactory

    @Binds
    abstract fun bindTextMessageSendScheduler(
        implementation: WorkManagerTextMessageSendScheduler,
    ): TextMessageSendScheduler

    @Binds
    abstract fun bindMediaMessageSendScheduler(
        implementation: WorkManagerMediaMessageSendScheduler,
    ): MediaMessageSendScheduler
}
