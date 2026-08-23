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

        val remoteMessage = try {
            remoteDataSource.insertTextMessage(
                messageId = messageId,
                senderId = senderId,
                text = text,
            )
        } catch (exception: Exception) {
            localDataSource.updateMessageSendState(
                messageId = messageId,
                status = MessageSendStatus.FAILED,
                attemptCount = INITIAL_SEND_ATTEMPT_COUNT,
                lastError = exception.message ?: UNKNOWN_SEND_ERROR,
            )
            throw exception
        }

        localDataSource.reconcileSentMessage(
            messageId = messageId,
            createdAt = remoteMessage.createdAt,
            updatedAt = remoteMessage.updatedAt,
            attemptCount = INITIAL_SEND_ATTEMPT_COUNT,
        )
    }

    override suspend fun sendMediaMessage(
        media: List<PendingMedia>,
        text: String?,
    ) = unsupported("sendMediaMessage")

    override suspend fun retryMessage(messageId: UUID) = unsupported("retryMessage")

    override suspend fun retryMediaItem(
        messageId: UUID,
        mediaId: UUID,
    ) = unsupported("retryMediaItem")

    override suspend fun startRealtimeSync() = unsupported("startRealtimeSync")

    override suspend fun stopRealtimeSync() = unsupported("stopRealtimeSync")

    private fun unsupported(operation: String): Nothing =
        throw UnsupportedOperationException("$operation is not implemented yet.")

    private companion object {
        const val INITIAL_SEND_ATTEMPT_COUNT = 1
        const val UNKNOWN_SEND_ERROR = "Remote text-message insert failed."
    }
}
