package com.example.chatapptask.data.chat.local

import com.example.chatapptask.core.database.dao.MessageDao
import com.example.chatapptask.core.database.dao.MessageMediaDao
import com.example.chatapptask.core.database.dao.UserDao
import com.example.chatapptask.core.database.entity.MessageEntity
import com.example.chatapptask.core.database.entity.MessageMediaEntity
import com.example.chatapptask.core.database.mapper.toDomain
import com.example.chatapptask.core.database.mapper.toEntity
import com.example.chatapptask.core.domain.model.MediaUploadStatus
import com.example.chatapptask.core.domain.model.Message
import com.example.chatapptask.core.domain.model.MessageMedia
import com.example.chatapptask.core.domain.model.MessageSendStatus
import com.example.chatapptask.core.domain.model.User
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class RoomChatLocalDataSource @Inject constructor(
    private val userDao: UserDao,
    private val messageDao: MessageDao,
    private val messageMediaDao: MessageMediaDao,
) : ChatLocalDataSource {
    override suspend fun upsertUser(user: User) {
        userDao.upsertUser(user.toEntity())
    }

    override suspend fun upsertUsers(users: List<User>) {
        userDao.upsertUsers(users.map { user -> user.toEntity() })
    }

    override suspend fun getUserById(userId: UUID): User? =
        userDao.getUserById(userId)?.toDomain()

    override fun observeUserById(userId: UUID): Flow<User?> =
        userDao.observeUserById(userId).map { user -> user?.toDomain() }

    override suspend fun upsertMessage(message: Message) {
        messageDao.upsertMessage(message.toEntity())
        if (message.media.isNotEmpty()) {
            messageMediaDao.upsertMedia(message.media.map { media -> media.toEntity() })
        }
    }

    override suspend fun deleteMessage(messageId: UUID) {
        messageDao.deleteMessage(messageId)
    }

    override suspend fun upsertMessages(messages: List<Message>) {
        messageDao.upsertMessages(messages.map { message -> message.toEntity() })
        val media = messages.flatMap(Message::media)
        if (media.isNotEmpty()) {
            messageMediaDao.upsertMedia(media.map { item -> item.toEntity() })
        }
    }

    override suspend fun getMessageById(messageId: UUID): Message? {
        val message = messageDao.getMessageById(messageId) ?: return null
        val media = messageMediaDao.getMediaForMessage(messageId)
        return message.toDomain(media)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMessages(): Flow<List<Message>> =
        messageDao.observeMessages().flatMapLatest { messages ->
            if (messages.isEmpty()) {
                flowOf(emptyList())
            } else {
                val messageIds = messages.map(MessageEntity::id)
                messageMediaDao.observeMediaForMessages(messageIds).map { media ->
                    mapMessagesWithMedia(messages, media)
                }
            }
        }

    override suspend fun getLatestMessages(limit: Int): List<Message> =
        mapMessagesWithMedia(messageDao.getLatestMessages(limit))

    override suspend fun getOlderMessages(
        cursorCreatedAt: Instant,
        cursorMessageId: UUID,
        limit: Int,
    ): List<Message> =
        mapMessagesWithMedia(
            messageDao.getOlderMessages(
                cursorCreatedAt = cursorCreatedAt,
                cursorMessageId = cursorMessageId,
                limit = limit,
            ),
        )

    override suspend fun beginMessageSendAttempt(messageId: UUID) {
        messageDao.beginSendAttempt(
            messageId = messageId,
            status = MessageSendStatus.SENDING,
        )
    }

    override suspend fun markMessageSendFailed(messageId: UUID, lastError: String?) {
        messageDao.markSendFailed(
            messageId = messageId,
            status = MessageSendStatus.FAILED,
            lastError = lastError,
        )
    }

    override suspend fun reconcileSentMessage(
        messageId: UUID,
        createdAt: Instant,
        updatedAt: Instant,
    ) {
        messageDao.updateAfterSendSuccess(
            messageId = messageId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            status = MessageSendStatus.SENT,
        )
    }

    override suspend fun getMessagesByStatuses(
        statuses: List<MessageSendStatus>,
    ): List<Message> =
        mapMessagesWithMedia(messageDao.getMessagesBySendStatuses(statuses))

    override suspend fun getOldestMessageBySendStatus(status: MessageSendStatus): Message? {
        val message = messageDao.getOldestMessageBySendStatus(status) ?: return null
        val media = messageMediaDao.getMediaForMessage(message.id)
        return message.toDomain(media)
    }

    override suspend fun upsertMedia(media: MessageMedia) {
        messageMediaDao.upsertMedia(media.toEntity())
    }

    override suspend fun upsertMedia(items: List<MessageMedia>) {
        messageMediaDao.upsertMedia(items.map { media -> media.toEntity() })
    }

    override suspend fun getMediaForMessage(messageId: UUID): List<MessageMedia> =
        messageMediaDao.getMediaForMessage(messageId).map { media -> media.toDomain() }

    override suspend fun getMediaForMessages(messageIds: List<UUID>): List<MessageMedia> =
        if (messageIds.isEmpty()) {
            emptyList()
        } else {
            messageMediaDao.getMediaForMessages(messageIds).map { media -> media.toDomain() }
        }

    override fun observeMediaForMessage(messageId: UUID): Flow<List<MessageMedia>> =
        messageMediaDao.observeMediaForMessage(messageId).map { media ->
            media.map { item -> item.toDomain() }
        }

    override suspend fun updateMediaUploadProgress(
        mediaId: UUID,
        status: MediaUploadStatus,
        progress: Int,
    ) {
        messageMediaDao.updateUploadProgress(
            mediaId = mediaId,
            status = status,
            progress = progress,
        )
    }

    override suspend fun markMediaUploaded(
        mediaId: UUID,
        storagePath: String,
    ) {
        messageMediaDao.markUploadSucceeded(
            mediaId = mediaId,
            storagePath = storagePath,
            status = MediaUploadStatus.UPLOADED,
        )
    }

    override suspend fun markMediaUploadFailed(
        mediaId: UUID,
        attemptCount: Int,
        error: String?,
    ) {
        messageMediaDao.markUploadFailed(
            mediaId = mediaId,
            status = MediaUploadStatus.FAILED,
            attemptCount = attemptCount,
            lastError = error,
        )
    }

    override suspend fun getMediaByStatuses(
        statuses: List<MediaUploadStatus>,
    ): List<MessageMedia> =
        messageMediaDao.getMediaByUploadStatuses(statuses).map { media -> media.toDomain() }

    override suspend fun deleteMediaForMessage(messageId: UUID) {
        messageMediaDao.deleteMediaForMessage(messageId)
    }

    private suspend fun mapMessagesWithMedia(messages: List<MessageEntity>): List<Message> {
        if (messages.isEmpty()) return emptyList()

        val messageIds = messages.map(MessageEntity::id)
        val media = messageMediaDao.getMediaForMessages(messageIds)
        return mapMessagesWithMedia(messages, media)
    }

    private fun mapMessagesWithMedia(
        messages: List<MessageEntity>,
        media: List<MessageMediaEntity>,
    ): List<Message> {
        val mediaByMessageId = media.groupBy(MessageMediaEntity::messageId)
        return messages.map { message ->
            message.toDomain(mediaByMessageId[message.id].orEmpty())
        }
    }
}
