package com.example.chatapptask.data.chat.local

import com.example.chatapptask.core.domain.model.MediaUploadStatus
import com.example.chatapptask.core.domain.model.Message
import com.example.chatapptask.core.domain.model.MessageMedia
import com.example.chatapptask.core.domain.model.MessageSendStatus
import com.example.chatapptask.core.domain.model.User
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface ChatLocalDataSource {
    suspend fun upsertUser(user: User)

    suspend fun upsertUsers(users: List<User>)

    suspend fun getUserById(userId: UUID): User?

    fun observeUserById(userId: UUID): Flow<User?>

    fun observeUsers(): Flow<List<User>>

    suspend fun upsertMessage(message: Message)

    suspend fun deleteMessage(messageId: UUID)

    suspend fun upsertMessages(messages: List<Message>)

    suspend fun getMessageById(messageId: UUID): Message?

    fun observeMessages(): Flow<List<Message>>

    suspend fun getLatestMessages(limit: Int): List<Message>

    suspend fun getOlderMessages(
        cursorCreatedAt: Instant,
        cursorMessageId: UUID,
        limit: Int,
    ): List<Message>

    suspend fun beginMessageSendAttempt(messageId: UUID)

    suspend fun markMessageSendFailed(messageId: UUID, lastError: String?)

    suspend fun reconcileSentMessage(
        messageId: UUID,
        createdAt: Instant,
        updatedAt: Instant,
    )

    suspend fun getMessagesByStatuses(
        statuses: List<MessageSendStatus>,
    ): List<Message>

    suspend fun getOldestMessageBySendStatus(status: MessageSendStatus): Message?

    suspend fun upsertMedia(media: MessageMedia)

    suspend fun upsertMedia(items: List<MessageMedia>)

    suspend fun getMediaForMessage(messageId: UUID): List<MessageMedia>

    suspend fun getMediaForMessages(messageIds: List<UUID>): List<MessageMedia>

    fun observeMediaForMessage(messageId: UUID): Flow<List<MessageMedia>>

    suspend fun beginMediaUploadAttempt(mediaId: UUID)

    suspend fun updateMediaUploadProgress(
        mediaId: UUID,
        status: MediaUploadStatus,
        progress: Int,
    )

    suspend fun markMediaUploaded(
        mediaId: UUID,
        storagePath: String,
    )

    suspend fun markMediaUploadFailed(
        mediaId: UUID,
        error: String?,
    )

    suspend fun getMediaByStatuses(
        statuses: List<MediaUploadStatus>,
    ): List<MessageMedia>

    suspend fun deleteMediaForMessage(messageId: UUID)
}
