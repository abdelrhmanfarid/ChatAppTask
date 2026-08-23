package com.example.chatapptask.data.chat.repository

import com.example.chatapptask.core.common.identity.UserIdentityStore
import com.example.chatapptask.core.domain.model.MediaUploadStatus
import com.example.chatapptask.core.domain.model.Message
import com.example.chatapptask.core.domain.model.MessageMedia
import com.example.chatapptask.core.domain.model.MessageSendStatus
import com.example.chatapptask.core.domain.model.User
import com.example.chatapptask.data.chat.local.ChatLocalDataSource
import com.example.chatapptask.data.chat.remote.ChatRemoteDataSource
import com.example.chatapptask.data.chat.worker.TextMessageSendScheduler
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultChatRepositoryTest {
    private val senderId = UUID.fromString("33eed91f-846c-49c8-851d-bca519b01432")
    private val serverCreatedAt = Instant.parse("2026-08-23T12:34:56Z")
    private val serverUpdatedAt = Instant.parse("2026-08-23T12:35:01Z")

    @Test
    fun sendTextMessage_persistsOnceBeforeSchedulingSameId_withoutRemoteCall() = runBlocking {
        val events = mutableListOf<String>()
        val local = RecordingLocalDataSource(events)
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt)
        val scheduler = RecordingTextMessageSendScheduler(events)
        val repository = createRepository(local, remote, scheduler)

        repository.sendTextMessage("Hello")

        val message = requireNotNull(local.persistedMessage)
        assertEquals(1, local.upsertCount)
        assertEquals(MessageSendStatus.SENDING, message.sendStatus)
        assertEquals(0, local.sendAttemptCount)
        assertEquals(senderId, message.senderId)
        assertEquals("Hello", message.textContent)
        assertTrue(message.media.isEmpty())
        assertEquals(listOf(message.id), scheduler.messageIds)
        assertTrue(remote.messageIds.isEmpty())
        assertEquals(listOf("local:SENDING", "scheduler:enqueue"), events)
    }

    @Test
    fun sendTextMessage_schedulingFailure_keepsMessageAndMarksFailed() = runBlocking {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("scheduler unavailable")
        val local = RecordingLocalDataSource(events)
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt)
        val scheduler = RecordingTextMessageSendScheduler(events, failure)
        val repository = createRepository(local, remote, scheduler)
        var thrown: Throwable? = null

        try {
            repository.sendTextMessage("Hello")
        } catch (exception: Throwable) {
            thrown = exception
        }

        val message = requireNotNull(local.persistedMessage)
        assertSame(failure, thrown)
        assertEquals(1, local.upsertCount)
        assertEquals(listOf(message.id), scheduler.messageIds)
        assertTrue(remote.messageIds.isEmpty())
        assertEquals(0, local.sendAttemptCount)
        assertEquals(MessageSendStatus.FAILED, requireNotNull(local.currentMessage).sendStatus)
        assertEquals("scheduler unavailable", local.stateUpdates.single().lastError)
        assertEquals(
            listOf("local:SENDING", "scheduler:enqueue", "local:FAILED"),
            events,
        )
    }

    @Test
    fun retryMessage_schedulesExistingId_withoutCreatingOrSendingDirectly() = runBlocking {
        val events = mutableListOf<String>()
        val messageId = UUID.fromString("dc4e6f23-5017-44de-bdf9-45c737a2dcc8")
        val local = RecordingLocalDataSource(events).apply {
            seedMessage(persistedTextMessage(messageId, MessageSendStatus.FAILED))
        }
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt)
        val scheduler = RecordingTextMessageSendScheduler(events)
        val repository = createRepository(local, remote, scheduler)

        repository.retryMessage(messageId)

        assertEquals(0, local.upsertCount)
        assertEquals(listOf(messageId), scheduler.messageIds)
        assertTrue(remote.messageIds.isEmpty())
        assertEquals(listOf("scheduler:enqueue"), events)
    }

    @Test
    fun retryMessage_missingLocalMessage_failsWithoutScheduling() = runBlocking {
        val messageId = UUID.fromString("a4558744-b5f6-4ca3-8f81-9ba9750565ea")
        val local = RecordingLocalDataSource(mutableListOf())
        val remote = RecordingRemoteDataSource(mutableListOf(), serverCreatedAt, serverUpdatedAt)
        val scheduler = RecordingTextMessageSendScheduler(mutableListOf())
        val repository = createRepository(local, remote, scheduler)
        var thrown: Throwable? = null

        try {
            repository.retryMessage(messageId)
        } catch (exception: Throwable) {
            thrown = exception
        }

        assertTrue(thrown is PersistedTextMessageNotFoundException)
        assertTrue(scheduler.messageIds.isEmpty())
        assertTrue(remote.messageIds.isEmpty())
        assertEquals(0, local.upsertCount)
    }

    @Test
    fun sendPersistedTextMessage_incrementsBeforeRemoteAndReconcilesSuccess() = runBlocking {
        val events = mutableListOf<String>()
        val messageId = UUID.fromString("e0a56a2f-f246-40fb-bab0-5f91bb62e06c")
        val local = RecordingLocalDataSource(events).apply {
            seedMessage(persistedTextMessage(messageId, MessageSendStatus.SENDING), attemptCount = 0)
        }
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt).apply {
            attemptCountProvider = { local.sendAttemptCount }
        }
        val repository = createRepository(local, remote, RecordingTextMessageSendScheduler(events))

        repository.sendPersistedTextMessage(messageId)

        val message = requireNotNull(local.currentMessage)
        assertEquals(listOf(messageId), remote.messageIds)
        assertEquals(listOf(1), remote.attemptCountsAtInsert)
        assertEquals(1, local.sendAttemptCount)
        assertEquals(MessageSendStatus.SENT, message.sendStatus)
        assertEquals(serverCreatedAt, message.createdAt)
        assertEquals(serverUpdatedAt, message.updatedAt)
        assertEquals(listOf("local:SENDING", "remote:insert", "local:SENT"), events)
    }

    @Test
    fun sendPersistedTextMessage_remoteFailure_marksFailedAndRethrows() = runBlocking {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("network unavailable")
        val messageId = UUID.fromString("f6a81485-77d2-4dd4-85ad-c80c509d5708")
        val local = RecordingLocalDataSource(events).apply {
            seedMessage(persistedTextMessage(messageId, MessageSendStatus.SENDING), attemptCount = 0)
        }
        val remote = RecordingRemoteDataSource(
            events,
            serverCreatedAt,
            serverUpdatedAt,
            failure,
        ).apply {
            attemptCountProvider = { local.sendAttemptCount }
        }
        val repository = createRepository(local, remote, RecordingTextMessageSendScheduler(events))
        var thrown: Throwable? = null

        try {
            repository.sendPersistedTextMessage(messageId)
        } catch (exception: Throwable) {
            thrown = exception
        }

        assertSame(failure, thrown)
        assertEquals(1, local.sendAttemptCount)
        assertEquals(listOf(1), remote.attemptCountsAtInsert)
        assertEquals(MessageSendStatus.FAILED, requireNotNull(local.currentMessage).sendStatus)
        assertEquals("network unavailable", local.stateUpdates.last().lastError)
        assertEquals(listOf("local:SENDING", "remote:insert", "local:FAILED"), events)
    }

    @Test
    fun sendPersistedTextMessage_failureThenRetry_countsBothAttemptsWithSameId() = runBlocking {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("network unavailable")
        val messageId = UUID.fromString("344938fd-2c79-49b1-93ce-c000c78e29ba")
        val local = RecordingLocalDataSource(events).apply {
            seedMessage(persistedTextMessage(messageId, MessageSendStatus.SENDING), attemptCount = 0)
        }
        val remote = RecordingRemoteDataSource(
            events,
            serverCreatedAt,
            serverUpdatedAt,
            failure,
        ).apply {
            attemptCountProvider = { local.sendAttemptCount }
        }
        val repository = createRepository(local, remote, RecordingTextMessageSendScheduler(events))

        try {
            repository.sendPersistedTextMessage(messageId)
        } catch (_: IllegalStateException) {
            // Expected first-attempt failure.
        }
        remote.failure = null
        repository.sendPersistedTextMessage(messageId)

        assertEquals(listOf(messageId, messageId), remote.messageIds)
        assertEquals(listOf(1, 2), remote.attemptCountsAtInsert)
        assertEquals(2, local.sendAttemptCount)
        assertEquals(MessageSendStatus.SENT, requireNotNull(local.currentMessage).sendStatus)
    }

    private fun createRepository(
        localDataSource: ChatLocalDataSource,
        remoteDataSource: ChatRemoteDataSource,
        scheduler: TextMessageSendScheduler,
    ): DefaultChatRepository =
        DefaultChatRepository(
            localDataSource = localDataSource,
            remoteDataSource = remoteDataSource,
            userIdentityStore = object : UserIdentityStore {
                override suspend fun getOrCreateUserId(): UUID = senderId
            },
            textMessageSendScheduler = scheduler,
        )

    private fun persistedTextMessage(messageId: UUID, status: MessageSendStatus): Message =
        Message(
            id = messageId,
            senderId = senderId,
            textContent = "Existing message",
            createdAt = Instant.parse("2026-08-23T12:00:00Z"),
            updatedAt = Instant.parse("2026-08-23T12:00:00Z"),
            media = emptyList(),
            sendStatus = status,
        )
}

private data class SendStateUpdate(
    val status: MessageSendStatus,
    val attemptCount: Int,
    val lastError: String?,
)

private class RecordingTextMessageSendScheduler(
    private val events: MutableList<String>,
    private val failure: Exception? = null,
) : TextMessageSendScheduler {
    val messageIds = mutableListOf<UUID>()

    override suspend fun enqueue(messageId: UUID) {
        messageIds += messageId
        events += "scheduler:enqueue"
        failure?.let { throw it }
    }
}

private class RecordingLocalDataSource(
    private val events: MutableList<String>,
) : ChatLocalDataSource {
    var persistedMessage: Message? = null
    var currentMessage: Message? = null
    var upsertCount = 0
    var sendAttemptCount = 0
    val stateUpdates = mutableListOf<SendStateUpdate>()

    override suspend fun upsertMessage(message: Message) {
        upsertCount += 1
        persistedMessage = message
        currentMessage = message
        sendAttemptCount = 0
        events += "local:${message.sendStatus}"
    }

    fun seedMessage(message: Message, attemptCount: Int = 1) {
        currentMessage = message
        sendAttemptCount = attemptCount
    }

    override suspend fun beginMessageSendAttempt(messageId: UUID) {
        sendAttemptCount += 1
        stateUpdates += SendStateUpdate(MessageSendStatus.SENDING, sendAttemptCount, null)
        currentMessage = requireNotNull(currentMessage).copy(sendStatus = MessageSendStatus.SENDING)
        events += "local:SENDING"
    }

    override suspend fun markMessageSendFailed(messageId: UUID, lastError: String?) {
        stateUpdates += SendStateUpdate(MessageSendStatus.FAILED, sendAttemptCount, lastError)
        currentMessage = requireNotNull(currentMessage).copy(sendStatus = MessageSendStatus.FAILED)
        events += "local:FAILED"
    }

    override suspend fun reconcileSentMessage(
        messageId: UUID,
        createdAt: Instant,
        updatedAt: Instant,
    ) {
        currentMessage = requireNotNull(currentMessage).copy(
            createdAt = createdAt,
            updatedAt = updatedAt,
            sendStatus = MessageSendStatus.SENT,
        )
        events += "local:SENT"
    }

    override suspend fun getMessageById(messageId: UUID): Message? =
        currentMessage?.takeIf { it.id == messageId }

    override fun observeMessages(): Flow<List<Message>> = emptyFlow()
    override suspend fun upsertUser(user: User) = unused()
    override suspend fun upsertUsers(users: List<User>) = unused()
    override suspend fun getUserById(userId: UUID): User? = unused()
    override fun observeUserById(userId: UUID): Flow<User?> = unused()
    override suspend fun upsertMessages(messages: List<Message>) = unused()
    override suspend fun getLatestMessages(limit: Int): List<Message> = unused()
    override suspend fun getOlderMessages(
        cursorCreatedAt: Instant,
        cursorMessageId: UUID,
        limit: Int,
    ): List<Message> = unused()
    override suspend fun getMessagesByStatuses(statuses: List<MessageSendStatus>): List<Message> =
        unused()
    override suspend fun upsertMedia(media: MessageMedia) = unused()
    override suspend fun upsertMedia(items: List<MessageMedia>) = unused()
    override suspend fun getMediaForMessage(messageId: UUID): List<MessageMedia> = unused()
    override suspend fun getMediaForMessages(messageIds: List<UUID>): List<MessageMedia> = unused()
    override fun observeMediaForMessage(messageId: UUID): Flow<List<MessageMedia>> = unused()
    override suspend fun updateMediaUploadProgress(
        mediaId: UUID,
        status: MediaUploadStatus,
        progress: Int,
    ) = unused()
    override suspend fun markMediaUploaded(mediaId: UUID, storagePath: String) = unused()
    override suspend fun markMediaUploadFailed(
        mediaId: UUID,
        attemptCount: Int,
        error: String?,
    ) = unused()
    override suspend fun getMediaByStatuses(statuses: List<MediaUploadStatus>): List<MessageMedia> =
        unused()
    override suspend fun deleteMediaForMessage(messageId: UUID) = unused()
}

private class RecordingRemoteDataSource(
    private val events: MutableList<String>,
    private val createdAt: Instant,
    private val updatedAt: Instant,
    failure: Exception? = null,
) : ChatRemoteDataSource {
    var failure: Exception? = failure
    var attemptCountProvider: (() -> Int)? = null
    val attemptCountsAtInsert = mutableListOf<Int>()
    val messageIds = mutableListOf<UUID>()

    override suspend fun insertTextMessage(
        messageId: UUID,
        senderId: UUID,
        text: String,
    ): Message {
        events += "remote:insert"
        attemptCountProvider?.invoke()?.let(attemptCountsAtInsert::add)
        messageIds += messageId
        failure?.let { throw it }
        return Message(
            id = messageId,
            senderId = senderId,
            textContent = text,
            createdAt = createdAt,
            updatedAt = updatedAt,
            media = emptyList(),
            sendStatus = MessageSendStatus.SENT,
        )
    }

    override suspend fun upsertUser(user: User): User = unused()
    override suspend fun getUser(userId: UUID): User? = unused()
    override suspend fun getLatestMessages(limit: Int): List<Message> = unused()
    override suspend fun getOlderMessages(
        cursorCreatedAt: Instant,
        cursorMessageId: UUID,
        limit: Int,
    ): List<Message> = unused()
    override suspend fun createMediaMessage(
        messageId: UUID,
        senderId: UUID,
        text: String?,
        media: List<MessageMedia>,
    ): Message = unused()
    override suspend fun uploadChatMedia(
        messageId: UUID,
        mediaId: UUID,
        extension: String,
        bytes: ByteArray,
        mimeType: String,
    ): String = unused()
    override suspend fun deleteChatMediaObject(storagePath: String) = unused()
}

private fun unused(): Nothing = error("Not used by this test.")
