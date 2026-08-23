package com.example.chatapptask.data.chat.remote

import com.example.chatapptask.core.domain.model.Message
import com.example.chatapptask.core.domain.model.MessageMedia
import com.example.chatapptask.core.domain.model.User
import java.time.Instant
import java.util.UUID

interface ChatRemoteDataSource {
    suspend fun upsertUser(user: User): User

    suspend fun getUser(userId: UUID): User?

    suspend fun getLatestMessages(limit: Int): List<Message>

    suspend fun getOlderMessages(
        cursorCreatedAt: Instant,
        cursorMessageId: UUID,
        limit: Int,
    ): List<Message>

    suspend fun insertTextMessage(
        messageId: UUID,
        senderId: UUID,
        text: String,
    ): Message

    suspend fun createMediaMessage(
        messageId: UUID,
        senderId: UUID,
        text: String?,
        media: List<MessageMedia>,
    ): Message

    suspend fun uploadChatMedia(
        messageId: UUID,
        mediaId: UUID,
        extension: String,
        bytes: ByteArray,
        mimeType: String,
    ): String

    suspend fun deleteChatMediaObject(storagePath: String)
}
