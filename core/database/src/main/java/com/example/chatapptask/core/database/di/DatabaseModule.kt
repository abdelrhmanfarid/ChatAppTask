package com.example.chatapptask.core.database.di

import android.content.Context
import androidx.room.Room
import com.example.chatapptask.core.database.ChatAppDatabase
import com.example.chatapptask.core.database.dao.MessageDao
import com.example.chatapptask.core.database.dao.MessageMediaDao
import com.example.chatapptask.core.database.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val CHAT_DATABASE_NAME = "chat_app.db"

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideChatAppDatabase(
        @ApplicationContext context: Context,
    ): ChatAppDatabase =
        Room.databaseBuilder(
            context,
            ChatAppDatabase::class.java,
            CHAT_DATABASE_NAME,
        ).build()

    @Provides
    fun provideUserDao(database: ChatAppDatabase): UserDao = database.userDao()

    @Provides
    fun provideMessageDao(database: ChatAppDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideMessageMediaDao(database: ChatAppDatabase): MessageMediaDao =
        database.messageMediaDao()
}
