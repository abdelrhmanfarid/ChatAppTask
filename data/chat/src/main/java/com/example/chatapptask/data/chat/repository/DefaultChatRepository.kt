package com.example.chatapptask.data.chat.repository

import com.example.chatapptask.core.common.identity.UserIdentityStore
import com.example.chatapptask.core.domain.model.MediaUploadStatus
import com.example.chatapptask.core.domain.model.Message
import com.example.chatapptask.core.domain.model.MessageMedia
import com.example.chatapptask.core.domain.model.MessageSendStatus
import com.example.chatapptask.core.domain.model.PendingMedia
import com.example.chatapptask.core.domain.repository.ChatRepository
import com.example.chatapptask.data.chat.local.ChatLocalDataSource
import com.example.chatapptask.data.chat.local.OutgoingMediaStore
import com.example.chatapptask.data.chat.local.fileExtensionFor
import com.example.chatapptask.data.chat.remote.ChatRemoteDataSource
import com.example.chatapptask.data.chat.worker.MediaMessageScheduleReason
import com.example.chatapptask.data.chat.worker.MediaMessageSendScheduler
import com.example.chatapptask.data.chat.worker.TextMessageSendScheduler
import com.example.chatapptask.data.chat.worker.TextMessageScheduleReason
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultChatRepository @Inject constructor(
    private val localDataSource: ChatLocalDataSource,
    private val remoteDataSource: ChatRemoteDataSource,
    private val userIdentityStore: UserIdentityStore,
    private val textMessageSendScheduler: TextMessageSendScheduler,
    private val mediaMessageSendScheduler: MediaMessageSendScheduler,
    private val outgoingMediaStore: OutgoingMediaStore,
) : ChatRepository {
    private val realtimeMutex = Mutex()
    private var realtimeJob: Job? = null

    override fun observeMessages(): Flow<List<Message>> = localDataSource.observeMessages()

    override suspend fun loadLatestMessages(limit: Int) {
        persistRemoteMessagePage(remoteDataSource.getLatestMessages(limit))
    }

    override suspend fun loadOlderMessages(limit: Int): Int {
        val cursor = localDataSource.getOldestMessageBySendStatus(MessageSendStatus.SENT)
            ?: return 0
        val page = remoteDataSource.getOlderMessages(
            cursorCreatedAt = cursor.createdAt,
            cursorMessageId = cursor.id,
            limit = limit,
        )
        persistRemoteMessagePage(page)
        return page.size
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
        mediaMessageSendScheduler.cancel(messageId)
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
    ) {
        require(media.isNotEmpty()) { MEDIA_COUNT_REQUIRED }
        require(media.size <= MAX_MEDIA_ITEMS) { MEDIA_COUNT_LIMIT }

        val senderId = userIdentityStore.getOrCreateUserId()
        val messageId = UUID.randomUUID()
        val now = Instant.now()
        try {
            val persistedMedia = media.mapIndexed { index, pending ->
                val mediaId = UUID.randomUUID()
                val durableUri = outgoingMediaStore.copyIncoming(
                    sourceUri = pending.localUri,
                    messageId = messageId,
                    mediaId = mediaId,
                    mimeType = pending.mimeType,
                )
                MessageMedia(
                    id = mediaId,
                    messageId = messageId,
                    storagePath = null,
                    mediaType = pending.mediaType,
                    mimeType = pending.mimeType,
                    position = index,
                    sizeBytes = pending.sizeBytes,
                    width = pending.width,
                    height = pending.height,
                    localUri = durableUri,
                    uploadStatus = MediaUploadStatus.PENDING,
                )
            }
            localDataSource.upsertMessage(
                Message(
                    id = messageId,
                    senderId = senderId,
                    textContent = text,
                    createdAt = now,
                    updatedAt = now,
                    media = persistedMedia,
                    sendStatus = MessageSendStatus.SENDING,
                ),
            )
        } catch (exception: Exception) {
            runCatching { outgoingMediaStore.deleteCopiedMedia(messageId) }
            runCatching { localDataSource.deleteMessage(messageId) }
            throw exception
        }

        schedulePersistedMediaMessage(messageId, MediaMessageScheduleReason.INITIAL)
    }

    /**
     * Uploads remaining attachments in position order, then creates the remote media message
     * with the same UUIDs. Room stays SENDING until remote creation succeeds.
     */
    internal suspend fun sendPersistedMediaMessage(
        messageId: UUID,
        onAttachmentProgress: suspend (current: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        val message = requirePersistedMediaMessage(messageId)
        localDataSource.beginMessageSendAttempt(messageId)

        try {
            val uploaded = message.media.mapIndexed { index, media ->
                coroutineContext.ensureActive()
                onAttachmentProgress(index + 1, message.media.size)
                ensureUploadedAttachment(media)
            }
            val remoteMessage = createOrReuseRemoteMediaMessage(
                messageId = messageId,
                senderId = message.senderId,
                text = message.textContent,
                media = uploaded,
            )
            localDataSource.reconcileSentMessage(
                messageId = messageId,
                createdAt = remoteMessage.createdAt,
                updatedAt = remoteMessage.updatedAt,
            )
            runCatching { outgoingMediaStore.deleteCopiedMedia(messageId) }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            localDataSource.markMessageSendFailed(
                messageId = messageId,
                lastError = exception.message ?: UNKNOWN_MEDIA_SEND_ERROR,
            )
            throw exception
        }
    }

    private suspend fun ensureUploadedAttachment(media: MessageMedia): MessageMedia {
        val existingPath = media.storagePath?.takeIf(String::isNotBlank)
        if (media.uploadStatus == MediaUploadStatus.UPLOADED && existingPath != null) {
            return media.copy(
                storagePath = existingPath,
                uploadStatus = MediaUploadStatus.UPLOADED,
            )
        }

        val localUri = media.localUri?.takeIf(String::isNotBlank)
            ?: throw PersistedMediaLocalFileMissingException(media.messageId, media.id)
        if (!outgoingMediaStore.hasReadableCopy(localUri)) {
            throw PersistedMediaLocalFileMissingException(media.messageId, media.id)
        }

        localDataSource.beginMediaUploadAttempt(media.id)
        try {
            coroutineContext.ensureActive()
            val storagePath = remoteDataSource.uploadChatMedia(
                messageId = media.messageId,
                mediaId = media.id,
                extension = fileExtensionFor(media.mimeType, localUri),
                bytes = outgoingMediaStore.readCopyBytes(localUri),
                mimeType = media.mimeType,
            )
            localDataSource.markMediaUploaded(media.id, storagePath)
            return media.copy(
                storagePath = storagePath,
                uploadStatus = MediaUploadStatus.UPLOADED,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            localDataSource.markMediaUploadFailed(
                mediaId = media.id,
                error = exception.message ?: UNKNOWN_MEDIA_UPLOAD_ERROR,
            )
            throw exception
        }
    }

    private suspend fun createOrReuseRemoteMediaMessage(
        messageId: UUID,
        senderId: UUID,
        text: String?,
        media: List<MessageMedia>,
    ): Message {
        runCatching { remoteDataSource.getMessage(messageId) }.getOrNull()?.let { return it }
        return try {
            remoteDataSource.createMediaMessage(
                messageId = messageId,
                senderId = senderId,
                text = text,
                media = media,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            runCatching { remoteDataSource.getMessage(messageId) }.getOrNull()
                ?: throw exception
        }
    }

    private suspend fun requirePersistedMediaMessage(messageId: UUID): Message {
        val message = localDataSource.getMessageById(messageId)
            ?: throw PersistedMediaMessageNotFoundException(messageId)
        if (message.media.isEmpty()) {
            throw PersistedMessageIsNotMediaException(messageId)
        }
        if (message.media.size > MAX_MEDIA_ITEMS) {
            throw PersistedMediaMessageInvalidException(
                "Media message $messageId has ${message.media.size} attachments.",
            )
        }
        val ordered = message.media.sortedBy(MessageMedia::position)
        ordered.forEachIndexed { index, media ->
            if (media.position != index) {
                throw PersistedMediaMessageInvalidException(
                    "Media message $messageId has invalid attachment positions.",
                )
            }
            if (media.uploadStatus == MediaUploadStatus.UPLOADED) return@forEachIndexed
            val localUri = media.localUri?.takeIf(String::isNotBlank)
                ?: throw PersistedMediaLocalFileMissingException(messageId, media.id)
            if (!outgoingMediaStore.hasReadableCopy(localUri)) {
                throw PersistedMediaLocalFileMissingException(messageId, media.id)
            }
        }
        return message.copy(media = ordered)
    }

    private suspend fun schedulePersistedMediaMessage(
        messageId: UUID,
        reason: MediaMessageScheduleReason,
    ) {
        try {
            mediaMessageSendScheduler.enqueue(messageId, reason)
        } catch (exception: Exception) {
            localDataSource.markMessageSendFailed(
                messageId = messageId,
                lastError = exception.message ?: UNKNOWN_MEDIA_SCHEDULING_ERROR,
            )
            throw exception
        }
    }

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
        // Keep Android-only media fields (localUri, upload attempts/progress/error).
        // MessageMedia.toEntity() would reset them if remote DTOs were upserted here.
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
        const val UNKNOWN_MEDIA_SCHEDULING_ERROR = "Media-message scheduling failed."
        const val UNKNOWN_MEDIA_SEND_ERROR = "Remote media-message send failed."
        const val UNKNOWN_MEDIA_UPLOAD_ERROR = "Media upload failed."
        const val CANCELLED_SEND_ERROR = "Send cancelled."
        const val MEDIA_COUNT_REQUIRED = "A media message requires at least one attachment."
        const val MEDIA_COUNT_LIMIT = "A media message can include at most 10 attachments."
        const val MAX_MEDIA_ITEMS = 10
    }
}

internal sealed class PersistedTextMessageException(message: String) : IllegalStateException(message)

internal class PersistedTextMessageNotFoundException(messageId: UUID) :
    PersistedTextMessageException("Text message $messageId does not exist locally.")

internal class PersistedMessageIsNotTextException(messageId: UUID) :
    PersistedTextMessageException("Message $messageId is not a text-only message.")

internal sealed class PersistedMediaMessageException(message: String) : IllegalStateException(message)

internal class PersistedMediaMessageNotFoundException(messageId: UUID) :
    PersistedMediaMessageException("Media message $messageId does not exist locally.")

internal class PersistedMessageIsNotMediaException(messageId: UUID) :
    PersistedMediaMessageException("Message $messageId is not a media message.")

internal class PersistedMediaMessageInvalidException(message: String) :
    PersistedMediaMessageException(message)

internal class PersistedMediaLocalFileMissingException(
    messageId: UUID,
    mediaId: UUID,
) : PersistedMediaMessageException(
    "Media message $messageId is missing a durable local file for $mediaId.",
)
