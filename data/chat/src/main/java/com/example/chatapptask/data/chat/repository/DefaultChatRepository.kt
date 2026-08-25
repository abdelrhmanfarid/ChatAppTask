package com.example.chatapptask.data.chat.repository

import com.example.chatapptask.core.common.identity.UserIdentityStore
import com.example.chatapptask.core.domain.model.Message
import com.example.chatapptask.core.domain.model.MessageSendStatus
import com.example.chatapptask.core.domain.model.PendingMedia
import com.example.chatapptask.core.domain.repository.ChatRepository
import com.example.chatapptask.data.chat.local.ChatLocalDataSource
import com.example.chatapptask.data.chat.remote.ChatRemoteDataSource
import com.example.chatapptask.data.chat.worker.TextMessageSendScheduler
import com.example.chatapptask.data.chat.worker.TextMessageScheduleReason
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultChatRepository @Inject constructor(
    private val localDataSource: ChatLocalDataSource,
    private val remoteDataSource: ChatRemoteDataSource,
    private val userIdentityStore: UserIdentityStore,
    private val textMessageSendScheduler: TextMessageSendScheduler,
) : ChatRepository {
    private val realtimeMutex = Mutex()
    private var realtimeJob: Job? = null

    override fun observeMessages(): Flow<List<Message>> = localDataSource.observeMessages()

    override suspend fun loadLatestMessages(limit: Int) {
        persistRemoteMessagePage(remoteDataSource.getLatestMessages(limit))
    }

    override suspend fun loadOlderMessages(
        oldestCreatedAt: Instant,
        oldestMessageId: UUID,
        limit: Int,
    ) {
        persistRemoteMessagePage(
            remoteDataSource.getOlderMessages(
                cursorCreatedAt = oldestCreatedAt,
                cursorMessageId = oldestMessageId,
                limit = limit,
            ),
        )
    }

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

        schedulePersistedTextMessage(messageId, TextMessageScheduleReason.INITIAL)
    }

    override suspend fun retryMessage(messageId: UUID) {
        requirePersistedTextMessage(messageId)
        schedulePersistedTextMessage(messageId, TextMessageScheduleReason.MANUAL_RETRY)
    }

    override suspend fun cancelOutgoingSend(messageId: UUID) {
        textMessageSendScheduler.cancel(messageId)
        val message = localDataSource.getMessageById(messageId) ?: return
        if (message.sendStatus == MessageSendStatus.SENDING) {
            localDataSource.markMessageSendFailed(
                messageId = messageId,
                lastError = CANCELLED_SEND_ERROR,
            )
        }
    }

    internal suspend fun sendPersistedTextMessage(messageId: UUID) {
        val message = requirePersistedTextMessage(messageId)

        localDataSource.beginMessageSendAttempt(messageId)

        val remoteMessage = try {
            remoteDataSource.insertTextMessage(
                messageId = messageId,
                senderId = message.senderId,
                text = requireNotNull(message.textContent),
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

    private suspend fun requirePersistedTextMessage(messageId: UUID): Message {
        val message = localDataSource.getMessageById(messageId)
            ?: throw PersistedTextMessageNotFoundException(messageId)
        if (message.textContent == null || message.media.isNotEmpty()) {
            throw PersistedMessageIsNotTextException(messageId)
        }
        return message
    }

    private suspend fun schedulePersistedTextMessage(
        messageId: UUID,
        reason: TextMessageScheduleReason,
    ) {
        try {
            textMessageSendScheduler.enqueue(messageId, reason)
        } catch (exception: Exception) {
            localDataSource.markMessageSendFailed(
                messageId = messageId,
                lastError = exception.message ?: UNKNOWN_SCHEDULING_ERROR,
            )
            throw exception
        }
    }

    override suspend fun sendMediaMessage(
        media: List<PendingMedia>,
        text: String?,
    ) = unsupported("sendMediaMessage")

    override suspend fun retryMediaItem(
        messageId: UUID,
        mediaId: UUID,
    ) = unsupported("retryMediaItem")

    override suspend fun startRealtimeSync() {
        realtimeMutex.withLock {
            if (realtimeJob?.isActive == true) return
            realtimeJob = coroutineContext[Job]
        }
        try {
            remoteDataSource.observeRemoteMessageIds().collect { messageId ->
                try {
                    ingestRemoteMessage(messageId)
                } catch (_: Exception) {
                    // Keep the subscription alive; Room is left unchanged.
                }
            }
        } finally {
            realtimeMutex.withLock {
                if (realtimeJob === coroutineContext[Job]) {
                    realtimeJob = null
                }
            }
        }
    }

    override suspend fun stopRealtimeSync() {
        realtimeMutex.withLock {
            realtimeJob?.cancel()
            realtimeJob = null
        }
    }

    private suspend fun ingestRemoteMessage(messageId: UUID) {
        val remoteMessage = remoteDataSource.getMessage(messageId) ?: return
        persistIncomingRemoteMessage(remoteMessage)
    }

    private suspend fun persistIncomingRemoteMessage(message: Message) {
        val sentMessage = message.copy(sendStatus = MessageSendStatus.SENT)
        val existing = localDataSource.getMessageById(sentMessage.id)
        if (existing == null) {
            persistRemoteMessagePage(listOf(sentMessage))
            return
        }
        persistSenders(listOf(sentMessage))
        if (sentMessage.media.isNotEmpty()) {
            localDataSource.upsertMedia(sentMessage.media)
        }
        localDataSource.reconcileSentMessage(
            messageId = sentMessage.id,
            createdAt = sentMessage.createdAt,
            updatedAt = sentMessage.updatedAt,
        )
    }

    private suspend fun persistRemoteMessagePage(messages: List<Message>) {
        if (messages.isEmpty()) return

        val sentMessages = messages.map { message ->
            message.copy(sendStatus = MessageSendStatus.SENT)
        }
        persistSenders(sentMessages)
        localDataSource.upsertMessages(sentMessages)
    }

    private suspend fun persistSenders(messages: List<Message>) {
        val usersToUpsert = messages.map { message -> message.senderId }
            .distinct()
            .mapNotNull { senderId ->
                if (localDataSource.getUserById(senderId) != null) {
                    null
                } else {
                    remoteDataSource.getUser(senderId)
                }
            }
        if (usersToUpsert.isNotEmpty()) {
            localDataSource.upsertUsers(usersToUpsert)
        }
    }

    private fun unsupported(operation: String): Nothing =
        throw UnsupportedOperationException("$operation is not implemented yet.")

    private companion object {
        const val UNKNOWN_SEND_ERROR = "Remote text-message insert failed."
        const val UNKNOWN_SCHEDULING_ERROR = "Text-message scheduling failed."
        const val CANCELLED_SEND_ERROR = "Send cancelled."
    }
}

internal sealed class PersistedTextMessageException(message: String) : IllegalStateException(message)

internal class PersistedTextMessageNotFoundException(messageId: UUID) :
    PersistedTextMessageException("Text message $messageId does not exist locally.")

internal class PersistedMessageIsNotTextException(messageId: UUID) :
    PersistedTextMessageException("Message $messageId is not a text-only message.")
