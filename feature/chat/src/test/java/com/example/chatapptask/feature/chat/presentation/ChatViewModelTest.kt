package com.example.chatapptask.feature.chat.presentation

import com.example.chatapptask.core.domain.model.Message
import com.example.chatapptask.core.domain.model.MessageSendStatus
import com.example.chatapptask.core.domain.model.PendingMedia
import com.example.chatapptask.core.domain.model.User
import com.example.chatapptask.core.domain.repository.ChatRepository
import com.example.chatapptask.core.domain.repository.UserRepository
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun repositoryMessages_areExposedInUiStateWithoutReordering() = runTest(dispatcher) {
        val repository = FakeChatRepository()
        val viewModel = ChatViewModel(repository, FakeUserRepository())
        val messages = listOf(message("00000000-0000-0000-0000-000000000002"), message("00000000-0000-0000-0000-000000000001"))

        repository.messages.value = messages
        advanceUntilIdle()

        assertEquals(messages, viewModel.uiState.value.messages)
    }

    @Test
    fun sendText_trimsAndSchedulesExpectedText_thenClearsComposer() = runTest(dispatcher) {
        val repository = FakeChatRepository()
        val viewModel = ChatViewModel(repository, FakeUserRepository())

        viewModel.onAction(ChatAction.ComposerTextChanged("  Hello  "))
        viewModel.onAction(ChatAction.SendText)
        advanceUntilIdle()

        assertEquals(listOf("Hello"), repository.sentTexts)
        assertEquals("", viewModel.uiState.value.composerText)
        assertFalse(viewModel.uiState.value.isSendRequestInProgress)
        assertTrue(viewModel.uiState.value.messages.isEmpty())
    }

    @Test
    fun blankText_doesNotSend() = runTest(dispatcher) {
        val repository = FakeChatRepository()
        val viewModel = ChatViewModel(repository, FakeUserRepository())

        viewModel.onAction(ChatAction.ComposerTextChanged("   \n  "))
        viewModel.onAction(ChatAction.SendText)
        advanceUntilIdle()

        assertTrue(repository.sentTexts.isEmpty())
    }

    @Test
    fun repeatedSendWhileRequestIsPending_isIgnored() = runTest(dispatcher) {
        val repository = FakeChatRepository()
        val viewModel = ChatViewModel(repository, FakeUserRepository())

        viewModel.onAction(ChatAction.ComposerTextChanged("Hello"))
        viewModel.onAction(ChatAction.SendText)
        viewModel.onAction(ChatAction.SendText)
        advanceUntilIdle()

        assertEquals(listOf("Hello"), repository.sentTexts)
    }

    @Test
    fun retryMessage_usesExistingUuid() = runTest(dispatcher) {
        val repository = FakeChatRepository()
        val viewModel = ChatViewModel(repository, FakeUserRepository())
        val messageId = UUID.fromString("3dc4cbbb-bf41-4306-ae07-362f6bc59091")

        viewModel.onAction(ChatAction.RetryMessage(messageId))
        advanceUntilIdle()

        assertEquals(listOf(messageId), repository.retriedMessageIds)
    }

    @Test
    fun schedulingException_emitsImmediateErrorWithoutRemovingComposerText() =
        runTest(dispatcher) {
            val repository = FakeChatRepository().apply {
                sendFailure = IllegalStateException("scheduler unavailable")
            }
            val viewModel = ChatViewModel(repository, FakeUserRepository())

            viewModel.onAction(ChatAction.ComposerTextChanged("Hello"))
            viewModel.onAction(ChatAction.SendText)
            advanceUntilIdle()

            assertEquals(
                ChatEvent.ShowError("scheduler unavailable"),
                viewModel.events.first(),
            )
            assertEquals("Hello", viewModel.uiState.value.composerText)
            assertFalse(viewModel.uiState.value.isSendRequestInProgress)
        }

    private fun message(id: String): Message =
        Message(
            id = UUID.fromString(id),
            senderId = UUID.fromString("33eed91f-846c-49c8-851d-bca519b01432"),
            textContent = "Message",
            createdAt = Instant.parse("2026-08-23T12:00:00Z"),
            updatedAt = Instant.parse("2026-08-23T12:00:00Z"),
            media = emptyList(),
            sendStatus = MessageSendStatus.SENT,
        )

    @Test
    fun currentUserId_isExposedForMessageOwnership() = runTest(dispatcher) {
        val repository = FakeChatRepository()
        val currentUserId = UUID.fromString("33eed91f-846c-49c8-851d-bca519b01432")
        val viewModel = ChatViewModel(repository, FakeUserRepository(currentUserId))

        advanceUntilIdle()

        assertEquals(currentUserId, viewModel.uiState.value.currentUserId)
    }
}

private class FakeChatRepository : ChatRepository {
    val messages = MutableStateFlow<List<Message>>(emptyList())
    val sentTexts = mutableListOf<String>()
    val retriedMessageIds = mutableListOf<UUID>()
    var sendFailure: Exception? = null

    override fun observeMessages(): Flow<List<Message>> = messages

    override suspend fun sendTextMessage(text: String) {
        sentTexts += text
        sendFailure?.let { throw it }
    }

    override suspend fun retryMessage(messageId: UUID) {
        retriedMessageIds += messageId
    }

    override suspend fun loadLatestMessages(limit: Int) = unused()
    override suspend fun loadOlderMessages(
        oldestCreatedAt: Instant,
        oldestMessageId: UUID,
        limit: Int,
    ) = unused()
    override suspend fun sendMediaMessage(media: List<PendingMedia>, text: String?) = unused()
    override suspend fun retryMediaItem(messageId: UUID, mediaId: UUID) = unused()
    override suspend fun startRealtimeSync() = unused()
    override suspend fun stopRealtimeSync() = unused()
}

private class FakeUserRepository(
    private val currentUserId: UUID = UUID.fromString("33eed91f-846c-49c8-851d-bca519b01432"),
) : UserRepository {
    override suspend fun getCurrentUserId(): UUID = currentUserId

    override suspend fun getUser(userId: UUID): User? = unused()

    override fun observeUser(userId: UUID): Flow<User?> = unused()

    override suspend fun upsertUser(user: User) = unused()
}

private fun unused(): Nothing = error("Not used by this test.")
