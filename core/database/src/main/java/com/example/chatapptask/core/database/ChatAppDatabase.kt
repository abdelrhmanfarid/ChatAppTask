package com.example.chatapptask.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.chatapptask.core.database.converter.DatabaseTypeConverters
import com.example.chatapptask.core.database.dao.MessageDao
import com.example.chatapptask.core.database.dao.MessageMediaDao
import com.example.chatapptask.core.database.dao.UserDao
import com.example.chatapptask.core.database.entity.MessageEntity
import com.example.chatapptask.core.database.entity.MessageMediaEntity
import com.example.chatapptask.core.database.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        MessageEntity::class,
        MessageMediaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DatabaseTypeConverters::class)
abstract class ChatAppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

    abstract fun messageDao(): MessageDao

    abstract fun messageMediaDao(): MessageMediaDao
}
