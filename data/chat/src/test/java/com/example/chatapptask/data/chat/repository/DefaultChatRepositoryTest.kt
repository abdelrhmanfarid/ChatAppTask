package com.example.chatapptask.data.chat.repository

import com.example.chatapptask.core.common.identity.UserIdentityStore
import com.example.chatapptask.core.domain.model.MediaUploadStatus
import com.example.chatapptask.core.domain.model.Message
import com.example.chatapptask.core.domain.model.MessageMedia
import com.example.chatapptask.core.domain.model.MessageSendStatus
import com.example.chatapptask.core.domain.model.User
import com.example.chatapptask.data.chat.local.ChatLocalDataSource
import com.example.chatapptask.data.chat.remote.ChatRemoteDataSource
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
    fun sendTextMessage_persistsSendingMessageBeforeRemoteInsert_andUsesSameId() = runBlocking {
        val events = mutableListOf<String>()
        val local = RecordingLocalDataSource(events)
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt)
        remote.attemptCountProvider = { local.sendAttemptCount }
        val repository = createRepository(local, remote)

        repository.sendTextMessage("Hello")

        val persistedMessage = requireNotNull(local.persistedMessage)
        assertEquals(MessageSendStatus.SENDING, persistedMessage.sendStatus)
        assertEquals(senderId, persistedMessage.senderId)
        assertEquals("Hello", persistedMessage.textContent)
        assertTrue(persistedMessage.media.isEmpty())
        assertEquals(0, local.attemptCountAtUpsert)
        assertEquals(listOf(1), remote.attemptCountsAtInsert)
        assertEquals(1, local.sendAttemptCount)
        assertEquals(persistedMessage.id, remote.messageId)
        assertEquals(senderId, remote.senderId)
        assertEquals("Hello", remote.text)
        assertEquals(
            listOf("local:SENDING", "local:SENDING", "remote:insert", "local:SENT"),
            events,
        )
    }

    @Test
    fun sendTextMessage_remoteSuccess_reconcilesSentStateAndServerTimestamps() = runBlocking {
        val local = RecordingLocalDataSource(mutableListOf())
        val remote = RecordingRemoteDataSource(
            events = mutableListOf(),
            createdAt = serverCreatedAt,
            updatedAt = serverUpdatedAt,
        )
        remote.attemptCountProvider = { local.sendAttemptCount }
        val repository = createRepository(local, remote)

        repository.sendTextMessage("Hello")

        val reconciledMessage = requireNotNull(local.currentMessage)
        assertEquals(
            SendSuccessUpdate(
                messageId = requireNotNull(local.persistedMessage).id,
                createdAt = serverCreatedAt,
                updatedAt = serverUpdatedAt,
                attemptCount = 1,
            ),
            local.successUpdates.single(),
        )
        assertEquals(0, local.attemptCountAtUpsert)
        assertEquals(listOf(1), remote.attemptCountsAtInsert)
        assertEquals(1, local.sendAttemptCount)
        assertEquals(requireNotNull(local.persistedMessage).id, reconciledMessage.id)
        assertEquals(MessageSendStatus.SENT, reconciledMessage.sendStatus)
        assertEquals(serverCreatedAt, reconciledMessage.createdAt)
        assertEquals(serverUpdatedAt, reconciledMessage.updatedAt)
    }

    @Test
    fun sendTextMessage_remoteFailure_marksFailedAndRethrows() = runBlocking {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("network unavailable")
        val local = RecordingLocalDataSource(events)
        val remote = RecordingRemoteDataSource(
            events = events,
            createdAt = serverCreatedAt,
            updatedAt = serverUpdatedAt,
            failure = failure,
        )
        remote.attemptCountProvider = { local.sendAttemptCount }
        val repository = createRepository(local, remote)
        var thrown: Throwable? = null

        try {
            repository.sendTextMessage("Hello")
        } catch (exception: Throwable) {
            thrown = exception
        }

        val persistedMessage = requireNotNull(local.persistedMessage)
        assertSame(failure, thrown)
        assertEquals(
            SendStateUpdate(
                messageId = persistedMessage.id,
                status = MessageSendStatus.FAILED,
                attemptCount = 1,
                lastError = "network unavailable",
            ),
            local.stateUpdates.last(),
        )
        assertEquals(
            listOf("local:SENDING", "local:SENDING", "remote:insert", "local:FAILED"),
            events,
        )
        assertEquals(MessageSendStatus.FAILED, requireNotNull(local.currentMessage).sendStatus)
        assertEquals(1, local.sendAttemptCount)
        assertEquals(listOf(1), remote.attemptCountsAtInsert)
        assertTrue(local.successUpdates.isEmpty())
    }

    @Test
    fun sendTextMessage_failureThenRetry_incrementsAttemptCountWithoutDuplicateMessage() =
        runBlocking {
            val events = mutableListOf<String>()
            val failure = IllegalStateException("network unavailable")
            val local = RecordingLocalDataSource(events)
            val remote = RecordingRemoteDataSource(
                events = events,
                createdAt = serverCreatedAt,
                updatedAt = serverUpdatedAt,
                failure = failure,
            )
            remote.attemptCountProvider = { local.sendAttemptCount }
            val repository = createRepository(local, remote)

            try {
                repository.sendTextMessage("Hello")
            } catch (_: IllegalStateException) {
                // The original failure is verified by the focused failure test.
            }
            val messageId = requireNotNull(local.persistedMessage).id
            remote.failure = null

            repository.retryMessage(messageId)

            assertEquals(1, local.upsertCount)
            assertEquals(listOf(messageId, messageId), remote.messageIds)
            assertEquals(listOf(1, 2), remote.attemptCountsAtInsert)
            assertEquals(2, local.sendAttemptCount)
            assertEquals(MessageSendStatus.SENT, requireNotNull(local.currentMessage).sendStatus)
            assertEquals(2, local.successUpdates.single().attemptCount)
        }

    @Test
    fun retryMessage_reusesExistingUuidWithoutCreatingAnotherLocalMessage() = runBlocking {
        val events = mutableListOf<String>()
        val messageId = UUID.fromString("dc4e6f23-5017-44de-bdf9-45c737a2dcc8")
        val local = RecordingLocalDataSource(events).apply {
            seedMessage(persistedTextMessage(messageId, MessageSendStatus.FAILED))
        }
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt)
        val repository = createRepository(local, remote)

        repository.retryMessage(messageId)

        val currentMessage = requireNotNull(local.currentMessage)
        assertEquals(0, local.upsertCount)
        assertEquals(messageId, remote.messageId)
        assertEquals(messageId, currentMessage.id)
        assertEquals(MessageSendStatus.SENT, currentMessage.sendStatus)
        assertEquals(serverCreatedAt, currentMessage.createdAt)
        assertEquals(serverUpdatedAt, currentMessage.updatedAt)
        assertEquals(
            listOf("local:SENDING", "remote:insert", "local:SENT"),
            events,
        )
    }

    @Test
    fun retryMessage_remoteFailure_marksExistingMessageFailed() = runBlocking {
        val events = mutableListOf<String>()
        val messageId = UUID.fromString("f6a81485-77d2-4dd4-85ad-c80c509d5708")
        val failure = IllegalStateException("network unavailable")
        val local = RecordingLocalDataSource(events).apply {
            seedMessage(persistedTextMessage(messageId, MessageSendStatus.FAILED))
        }
        val remote = RecordingRemoteDataSource(
            events = events,
            createdAt = serverCreatedAt,
            updatedAt = serverUpdatedAt,
            failure = failure,
        )
        val repository = createRepository(local, remote)
        var thrown: Throwable? = null

        try {
            repository.retryMessage(messageId)
        } catch (exception: Throwable) {
            thrown = exception
        }

        assertSame(failure, thrown)
        assertEquals(0, local.upsertCount)
        assertEquals(messageId, remote.messageId)
        assertEquals(MessageSendStatus.FAILED, requireNotNull(local.currentMessage).sendStatus)
        assertEquals(
            listOf(MessageSendStatus.SENDING, MessageSendStatus.FAILED),
            local.stateUpdates.map(SendStateUpdate::status),
        )
        assertEquals("network unavailable", local.stateUpdates.last().lastError)
        assertEquals(
            listOf("local:SENDING", "remote:insert", "local:FAILED"),
            events,
        )
    }

    @Test
    fun retryMessage_missingLocalMessage_failsDeterministically() = runBlocking {
        val messageId = UUID.fromString("a4558744-b5f6-4ca3-8f81-9ba9750565ea")
        val local = RecordingLocalDataSource(mutableListOf())
        val remote = RecordingRemoteDataSource(
            events = mutableListOf(),
            createdAt = serverCreatedAt,
            updatedAt = serverUpdatedAt,
        )
        val repository = createRepository(local, remote)
        var thrown: Throwable? = null

        try {
            repository.retryMessage(messageId)
        } catch (exception: Throwable) {
            thrown = exception
        }

        assertTrue(thrown is PersistedTextMessageNotFoundException)
        assertEquals(0, local.upsertCount)
        assertEquals(null, remote.messageId)
        assertTrue(local.stateUpdates.isEmpty())
        assertTrue(local.successUpdates.isEmpty())
    }

    private fun createRepository(
        localDataSource: ChatLocalDataSource,
        remoteDataSource: ChatRemoteDataSource,
    ): DefaultChatRepository =
        DefaultChatRepository(
            localDataSource = localDataSource,
            remoteDataSource = remoteDataSource,
            userIdentityStore = object : UserIdentityStore {
                override suspend fun getOrCreateUserId(): UUID = senderId
            },
        )

    private fun persistedTextMessage(
        messageId: UUID,
        status: MessageSendStatus,
    ): Message =
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
    val messageId: UUID,
    val status: MessageSendStatus,
    val attemptCount: Int,
    val lastError: String?,
)

private data class SendSuccessUpdate(
    val messageId: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
    val attemptCount: Int,
)

private class RecordingLocalDataSource(
    private val events: MutableList<String>,
) : ChatLocalDataSource {
    var persistedMessage: Message? = null
    var currentMessage: Message? = null
    var upsertCount = 0
    var sendAttemptCount = 0
    var attemptCountAtUpsert: Int? = null
    val stateUpdates = mutableListOf<SendStateUpdate>()
    val successUpdates = mutableListOf<SendSuccessUpdate>()

    override suspend fun upsertMessage(message: Message) {
        upsertCount += 1
        persistedMessage = message
        currentMessage = message
        sendAttemptCount = 0
        attemptCountAtUpsert = sendAttemptCount
        events += "local:${message.sendStatus}"
    }

    fun seedMessage(message: Message, attemptCount: Int = 1) {
        currentMessage = message
        sendAttemptCount = attemptCount
    }

    override suspend fun beginMessageSendAttempt(messageId: UUID) {
        sendAttemptCount += 1
        stateUpdates += SendStateUpdate(
            messageId = messageId,
            status = MessageSendStatus.SENDING,
            attemptCount = sendAttemptCount,
            lastError = null,
        )
        currentMessage = requireNotNull(currentMessage).copy(sendStatus = MessageSendStatus.SENDING)
        events += "local:SENDING"
    }

    override suspend fun markMessageSendFailed(messageId: UUID, lastError: String?) {
        stateUpdates += SendStateUpdate(
            messageId = messageId,
            status = MessageSendStatus.FAILED,
            attemptCount = sendAttemptCount,
            lastError = lastError,
        )
        currentMessage = requireNotNull(currentMessage).copy(sendStatus = MessageSendStatus.FAILED)
        events += "local:FAILED"
    }

    override suspend fun reconcileSentMessage(
        messageId: UUID,
        createdAt: Instant,
        updatedAt: Instant,
    ) {
        successUpdates += SendSuccessUpdate(messageId, createdAt, updatedAt, sendAttemptCount)
        currentMessage = requireNotNull(currentMessage).copy(
            createdAt = createdAt,
            updatedAt = updatedAt,
            sendStatus = MessageSendStatus.SENT,
        )
        events += "local:SENT"
    }

    override fun observeMessages(): Flow<List<Message>> = emptyFlow()

    override suspend fun upsertUser(user: User) = unused()
    override suspend fun upsertUsers(users: List<User>) = unused()
    override suspend fun getUserById(userId: UUID): User? = unused()
    override fun observeUserById(userId: UUID): Flow<User?> = unused()
    override suspend fun upsertMessages(messages: List<Message>) = unused()
    override suspend fun getMessageById(messageId: UUID): Message? =
        currentMessage?.takeIf { message -> message.id == messageId }
    override suspend fun getLatestMessages(limit: Int): List<Message> = unused()
    override suspend fun getOlderMessages(
        cursorCreatedAt: Instant,
        cursorMessageId: UUID,
        limit: Int,
    ): List<Message> = unused()
    override suspend fun getMessagesByStatuses(
        statuses: List<MessageSendStatus>,
    ): List<Message> = unused()
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
    override suspend fun getMediaByStatuses(
        statuses: List<MediaUploadStatus>,
    ): List<MessageMedia> = unused()
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
    var messageId: UUID? = null
    var senderId: UUID? = null
    var text: String? = null

    override suspend fun insertTextMessage(
        messageId: UUID,
        senderId: UUID,
        text: String,
    ): Message {
        events += "remote:insert"
        attemptCountProvider?.invoke()?.let(attemptCountsAtInsert::add)
        messageIds += messageId
        this.messageId = messageId
        this.senderId = senderId
        this.text = text
        failure?.let { exception -> throw exception }
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
