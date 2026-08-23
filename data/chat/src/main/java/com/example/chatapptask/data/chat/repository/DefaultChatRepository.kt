package com.example.chatapptask.data.chat.repository

import com.example.chatapptask.core.common.identity.UserIdentityStore
import com.example.chatapptask.core.domain.model.Message
import com.example.chatapptask.core.domain.model.MessageSendStatus
import com.example.chatapptask.core.domain.model.PendingMedia
import com.example.chatapptask.core.domain.repository.ChatRepository
import com.example.chatapptask.data.chat.local.ChatLocalDataSource
import com.example.chatapptask.data.chat.remote.ChatRemoteDataSource
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class DefaultChatRepository @Inject constructor(
    private val localDataSource: ChatLocalDataSource,
    private val remoteDataSource: ChatRemoteDataSource,
    private val userIdentityStore: UserIdentityStore,
) : ChatRepository {
    override fun observeMessages(): Flow<List<Message>> = localDataSource.observeMessages()

    override suspend fun loadLatestMessages(limit: Int) = unsupported("loadLatestMessages")

    override suspend fun loadOlderMessages(
        oldestCreatedAt: Instant,
        oldestMessageId: UUID,
        limit: Int,
    ) = unsupported("loadOlderMessages")

    override suspend fun sendTextMessage(text: String) {
        val messageId = UUID.randomUUID()
        val senderId = userIdentityStore.getOrCreateUserId()
        val now = Instant.now()

        localDataSource.upsertMessage(
            Message(
                id = messageId,
                senderId = senderId,
                textContent = text,
                createdAt = now,
                updatedAt = now,
                media = emptyList(),
                sendStatus = MessageSendStatus.SENDING,
            ),
        )

        sendPersistedTextMessage(messageId)
    }

    override suspend fun retryMessage(messageId: UUID) {
        sendPersistedTextMessage(messageId)
    }

    private suspend fun sendPersistedTextMessage(messageId: UUID) {
        val message = localDataSource.getMessageById(messageId)
            ?: throw PersistedTextMessageNotFoundException(messageId)
        val text = message.textContent
            ?: throw PersistedMessageIsNotTextException(messageId)
        if (message.media.isNotEmpty()) {
            throw PersistedMessageIsNotTextException(messageId)
        }

        localDataSource.beginMessageSendAttempt(messageId)

        val remoteMessage = try {
            remoteDataSource.insertTextMessage(
                messageId = messageId,
                senderId = message.senderId,
                text = text,
            )
        } catch (exception: Exception) {
            localDataSource.markMessageSendFailed(
                messageId = messageId,
                lastError = exception.message ?: UNKNOWN_SEND_ERROR,
            )
            throw exception
        }

        localDataSource.reconcileSentMessage(
            messageId = messageId,
            createdAt = remoteMessage.createdAt,
            updatedAt = remoteMessage.updatedAt,
        )
    }

    override suspend fun sendMediaMessage(
        media: List<PendingMedia>,
        text: String?,
    ) = unsupported("sendMediaMessage")

    override suspend fun retryMediaItem(
        messageId: UUID,
        mediaId: UUID,
    ) = unsupported("retryMediaItem")

    override suspend fun startRealtimeSync() = unsupported("startRealtimeSync")

    override suspend fun stopRealtimeSync() = unsupported("stopRealtimeSync")

    private fun unsupported(operation: String): Nothing =
        throw UnsupportedOperationException("$operation is not implemented yet.")

    private companion object {
        const val UNKNOWN_SEND_ERROR = "Remote text-message insert failed."
    }
}

internal sealed class PersistedTextMessageException(message: String) : IllegalStateException(message)

internal class PersistedTextMessageNotFoundException(messageId: UUID) :
    PersistedTextMessageException("Text message $messageId does not exist locally.")

internal class PersistedMessageIsNotTextException(messageId: UUID) :
    PersistedTextMessageException("Message $messageId is not a text-only message.")
