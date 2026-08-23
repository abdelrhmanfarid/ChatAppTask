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
        val repository = createRepository(local, remote)

        repository.sendTextMessage("Hello")

        val persistedMessage = requireNotNull(local.persistedMessage)
        assertEquals(MessageSendStatus.SENDING, persistedMessage.sendStatus)
        assertEquals(senderId, persistedMessage.senderId)
        assertEquals("Hello", persistedMessage.textContent)
        assertTrue(persistedMessage.media.isEmpty())
        assertEquals(persistedMessage.id, remote.messageId)
        assertEquals(senderId, remote.senderId)
        assertEquals("Hello", remote.text)
        assertEquals(
            listOf("local:SENDING", "remote:insert", "local:SENT"),
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
            local.stateUpdates.single(),
        )
        assertEquals(
            listOf("local:SENDING", "remote:insert", "local:FAILED"),
            events,
        )
        assertEquals(MessageSendStatus.FAILED, requireNotNull(local.currentMessage).sendStatus)
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
    val stateUpdates = mutableListOf<SendStateUpdate>()
    val successUpdates = mutableListOf<SendSuccessUpdate>()

    override suspend fun upsertMessage(message: Message) {
        persistedMessage = message
        currentMessage = message
        events += "local:${message.sendStatus}"
    }

    override suspend fun updateMessageSendState(
        messageId: UUID,
        status: MessageSendStatus,
        attemptCount: Int,
        lastError: String?,
    ) {
        stateUpdates += SendStateUpdate(messageId, status, attemptCount, lastError)
        currentMessage = requireNotNull(currentMessage).copy(sendStatus = status)
        events += "local:$status"
    }

    override suspend fun reconcileSentMessage(
        messageId: UUID,
        createdAt: Instant,
        updatedAt: Instant,
        attemptCount: Int,
    ) {
        successUpdates += SendSuccessUpdate(messageId, createdAt, updatedAt, attemptCount)
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
    override suspend fun getMessageById(messageId: UUID): Message? = unused()
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
    private val failure: Exception? = null,
) : ChatRemoteDataSource {
    var messageId: UUID? = null
    var senderId: UUID? = null
    var text: String? = null

    override suspend fun insertTextMessage(
        messageId: UUID,
        senderId: UUID,
        text: String,
    ): Message {
        events += "remote:insert"
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
