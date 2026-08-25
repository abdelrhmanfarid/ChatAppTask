package com.example.chatapptask.feature.chat.presentation

import com.example.chatapptask.core.domain.model.MediaType
import com.example.chatapptask.core.domain.model.Message
import com.example.chatapptask.core.domain.model.MessageSendStatus
import com.example.chatapptask.core.domain.model.PendingMedia
import com.example.chatapptask.core.domain.model.User
import com.example.chatapptask.core.domain.repository.ChatRepository
import com.example.chatapptask.core.domain.repository.UserRepository
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
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
        assertTrue(repository.sentMedia.isEmpty())
        assertEquals("", viewModel.uiState.value.composerText)
        assertFalse(viewModel.uiState.value.isSendRequestInProgress)
        assertTrue(viewModel.uiState.value.messages.isEmpty())
    }

    @Test
    fun mediaSelection_isStoredInOrder() = runTest(dispatcher) {
        val viewModel = ChatViewModel(FakeChatRepository(), FakeUserRepository())
        val first = attachment("content://media/1", MediaType.IMAGE, "image/jpeg")
        val second = attachment("content://media/2", MediaType.VIDEO, "video/mp4")

        viewModel.onAction(ChatAction.MediaSelected(listOf(first, second)))
        advanceUntilIdle()

        assertEquals(listOf(first, second), viewModel.uiState.value.selectedAttachments)
    }

    @Test
    fun mediaSelection_enforcesMaximumOfTen() = runTest(dispatcher) {
        val viewModel = ChatViewModel(FakeChatRepository(), FakeUserRepository())
        val initial = (1..8).map { index ->
            attachment("content://media/$index", MediaType.IMAGE, "image/jpeg")
        }
        val extra = (9..12).map { index ->
            attachment("content://media/$index", MediaType.IMAGE, "image/jpeg")
        }

        viewModel.onAction(ChatAction.MediaSelected(initial))
        viewModel.onAction(ChatAction.MediaSelected(extra))
        advanceUntilIdle()

        assertEquals(10, viewModel.uiState.value.selectedAttachments.size)
        assertEquals(
            (1..10).map { "content://media/$it" },
            viewModel.uiState.value.selectedAttachments.map { it.uri },
        )
    }

    @Test
    fun removeSelectedMedia_removesIndividualItem() = runTest(dispatcher) {
        val viewModel = ChatViewModel(FakeChatRepository(), FakeUserRepository())
        val first = attachment("content://media/1", MediaType.IMAGE, "image/jpeg")
        val second = attachment("content://media/2", MediaType.VIDEO, "video/mp4")
        val third = attachment("content://media/3", MediaType.IMAGE, "image/png")

        viewModel.onAction(ChatAction.MediaSelected(listOf(first, second, third)))
        viewModel.onAction(ChatAction.RemoveSelectedMedia(second.uri))
        advanceUntilIdle()

        assertEquals(listOf(first, third), viewModel.uiState.value.selectedAttachments)
    }

    @Test
    fun mediaOnlySend_invokesMediaPathAndClearsSelection() = runTest(dispatcher) {
        val repository = FakeChatRepository()
        val viewModel = ChatViewModel(repository, FakeUserRepository())
        val media = listOf(
            attachment("content://media/1", MediaType.IMAGE, "image/jpeg"),
            attachment("content://media/2", MediaType.VIDEO, "video/mp4"),
        )

        viewModel.onAction(ChatAction.MediaSelected(media))
        viewModel.onAction(ChatAction.SendText)
        advanceUntilIdle()

        assertTrue(repository.sentTexts.isEmpty())
        assertEquals(1, repository.sentMedia.size)
        assertEquals(
            media.map { it.toPendingMedia() },
            repository.sentMedia.single().media,
        )
        assertEquals(null, repository.sentMedia.single().text)
        assertTrue(viewModel.uiState.value.selectedAttachments.isEmpty())
        assertEquals("", viewModel.uiState.value.composerText)
        assertFalse(viewModel.uiState.value.isSendRequestInProgress)
    }

    @Test
    fun mediaPlusTextSend_invokesMediaPathWithTrimmedText() = runTest(dispatcher) {
        val repository = FakeChatRepository()
        val viewModel = ChatViewModel(repository, FakeUserRepository())
        val media = listOf(attachment("content://media/1", MediaType.IMAGE, "image/jpeg"))

        viewModel.onAction(ChatAction.MediaSelected(media))
        viewModel.onAction(ChatAction.ComposerTextChanged("  Caption  "))
        viewModel.onAction(ChatAction.SendText)
        advanceUntilIdle()

        assertTrue(repository.sentTexts.isEmpty())
        assertEquals(1, repository.sentMedia.size)
        assertEquals("Caption", repository.sentMedia.single().text)
        assertTrue(viewModel.uiState.value.selectedAttachments.isEmpty())
        assertEquals("", viewModel.uiState.value.composerText)
    }

    @Test
    fun mediaSendFailure_keepsSelectionAndComposerText() = runTest(dispatcher) {
        val repository = FakeChatRepository().apply {
            mediaSendFailure = IllegalStateException("media scheduler unavailable")
        }
        val viewModel = ChatViewModel(repository, FakeUserRepository())
        val media = listOf(attachment("content://media/1", MediaType.IMAGE, "image/jpeg"))

        viewModel.onAction(ChatAction.MediaSelected(media))
        viewModel.onAction(ChatAction.ComposerTextChanged("Keep me"))
        viewModel.onAction(ChatAction.SendText)
        advanceUntilIdle()

        assertEquals(
            ChatEvent.ShowError("media scheduler unavailable"),
            viewModel.events.first(),
        )
        assertEquals(media, viewModel.uiState.value.selectedAttachments)
        assertEquals("Keep me", viewModel.uiState.value.composerText)
        assertFalse(viewModel.uiState.value.isSendRequestInProgress)
    }

    @Test
    fun attachmentClicked_requestsPickerWithRemainingCapacity() = runTest(dispatcher) {
        val viewModel = ChatViewModel(FakeChatRepository(), FakeUserRepository())
        viewModel.onAction(
            ChatAction.MediaSelected(
                listOf(attachment("content://media/1", MediaType.IMAGE, "image/jpeg")),
            ),
        )
        viewModel.onAction(ChatAction.AttachmentClicked)
        advanceUntilIdle()

        assertEquals(ChatEvent.OpenMediaPicker(maxItems = 9), viewModel.events.first())
    }

    @Test
    fun attachmentClicked_atLimit_emitsErrorWithoutPicker() = runTest(dispatcher) {
        val viewModel = ChatViewModel(FakeChatRepository(), FakeUserRepository())
        viewModel.onAction(
            ChatAction.MediaSelected(
                (1..10).map { index ->
                    attachment("content://media/$index", MediaType.IMAGE, "image/jpeg")
                },
            ),
        )
        viewModel.onAction(ChatAction.AttachmentClicked)
        advanceUntilIdle()

        assertEquals(
            ChatEvent.ShowError("You can attach up to 10 photos or videos."),
            viewModel.events.first(),
        )
    }

    @Test
    fun blankTextWithoutMedia_doesNotSend() = runTest(dispatcher) {
        val repository = FakeChatRepository()
        val viewModel = ChatViewModel(repository, FakeUserRepository())

        viewModel.onAction(ChatAction.ComposerTextChanged("   \n  "))
        viewModel.onAction(ChatAction.SendText)
        advanceUntilIdle()

        assertTrue(repository.sentTexts.isEmpty())
        assertTrue(repository.sentMedia.isEmpty())
    }

    @Test
    fun mediaSelection_skipsDuplicateUris() = runTest(dispatcher) {
        val viewModel = ChatViewModel(FakeChatRepository(), FakeUserRepository())
        val first = attachment("content://media/1", MediaType.IMAGE, "image/jpeg")
        val duplicate = attachment("content://media/1", MediaType.IMAGE, "image/jpeg")
        val second = attachment("content://media/2", MediaType.VIDEO, "video/mp4")

        viewModel.onAction(ChatAction.MediaSelected(listOf(first)))
        viewModel.onAction(ChatAction.MediaSelected(listOf(duplicate, second)))
        advanceUntilIdle()

        assertEquals(listOf(first, second), viewModel.uiState.value.selectedAttachments)
    }

    @Test
    fun emptyMediaSelected_isIgnored() = runTest(dispatcher) {
        val viewModel = ChatViewModel(FakeChatRepository(), FakeUserRepository())

        viewModel.onAction(ChatAction.MediaSelected(emptyList()))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.selectedAttachments.isEmpty())
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

    private fun message(
        id: String,
        createdAt: String = "2026-08-23T12:00:00Z",
        status: MessageSendStatus = MessageSendStatus.SENT,
    ): Message =
        Message(
            id = UUID.fromString(id),
            senderId = UUID.fromString("33eed91f-846c-49c8-851d-bca519b01432"),
            textContent = "Message",
            createdAt = Instant.parse(createdAt),
            updatedAt = Instant.parse(createdAt),
            media = emptyList(),
            sendStatus = status,
        )

    @Test
    fun currentUserId_isExposedForMessageOwnership() = runTest(dispatcher) {
        val repository = FakeChatRepository()
        val currentUserId = UUID.fromString("33eed91f-846c-49c8-851d-bca519b01432")
        val viewModel = ChatViewModel(repository, FakeUserRepository(currentUserId))

        advanceUntilIdle()

        assertEquals(currentUserId, viewModel.uiState.value.currentUserId)
    }

    @Test
    fun start_loadsLatestMessagesOnce() = runTest(dispatcher) {
        val repository = FakeChatRepository()
        ChatViewModel(repository, FakeUserRepository())

        advanceUntilIdle()

        assertEquals(1, repository.loadLatestCount)
    }

    @Test
    fun latestLoadFailure_emitsErrorAndKeepsObservedMessages() = runTest(dispatcher) {
        val existing = message("00000000-0000-0000-0000-000000000001")
        val repository = FakeChatRepository().apply {
            messages.value = listOf(existing)
            loadLatestFailure = IllegalStateException("network unavailable")
        }
        val viewModel = ChatViewModel(repository, FakeUserRepository())

        advanceUntilIdle()

        assertEquals(
            ChatEvent.ShowError("network unavailable"),
            viewModel.events.first(),
        )
        assertEquals(listOf(existing), viewModel.uiState.value.messages)
    }

    @Test
    fun start_startsRealtimeThenLoadsLatest() = runTest(dispatcher) {
        val repository = FakeChatRepository()
        ChatViewModel(repository, FakeUserRepository())

        advanceUntilIdle()

        assertEquals(1, repository.startRealtimeCount)
        assertEquals(1, repository.loadLatestCount)
    }

    @Test
    fun realtimeStartFailure_stillLoadsLatestAndKeepsMessages() = runTest(dispatcher) {
        val existing = message("00000000-0000-0000-0000-000000000001")
        val repository = FakeChatRepository().apply {
            messages.value = listOf(existing)
            startRealtimeFailure = IllegalStateException("realtime unavailable")
        }
        val viewModel = ChatViewModel(repository, FakeUserRepository())

        advanceUntilIdle()

        assertEquals(
            ChatEvent.ShowError("realtime unavailable"),
            viewModel.events.first(),
        )
        assertEquals(1, repository.loadLatestCount)
        assertEquals(listOf(existing), viewModel.uiState.value.messages)
    }

    @Test
    fun loadOlderMessages_delegatesToRepositoryWhenSentMessagesExist() = runTest(dispatcher) {
        val olderSent = message(
            id = "00000000-0000-0000-0000-000000000001",
            createdAt = "2026-08-23T11:00:00Z",
        )
        val newerSent = message(
            id = "00000000-0000-0000-0000-000000000002",
            createdAt = "2026-08-23T12:00:00Z",
        )
        val repository = FakeChatRepository().apply {
            messages.value = listOf(newerSent, olderSent)
        }
        val viewModel = ChatViewModel(repository, FakeUserRepository())
        advanceUntilIdle()

        viewModel.onAction(ChatAction.LoadOlderMessages)
        advanceUntilIdle()

        assertEquals(1, repository.olderLoadCount)
        assertFalse(viewModel.uiState.value.isLoadingOlder)
        assertEquals(listOf(newerSent, olderSent), viewModel.uiState.value.messages)
    }

    @Test
    fun loadOlderMessages_stillLoadsWhenOldestObservedRowIsFailed() = runTest(dispatcher) {
        val failedOlder = message(
            id = "00000000-0000-0000-0000-000000000050",
            createdAt = "2026-08-23T10:50:00Z",
            status = MessageSendStatus.FAILED,
        )
        val oldestSent = message(
            id = "00000000-0000-0000-0000-000000000081",
            createdAt = "2026-08-23T12:00:00Z",
        )
        val repository = FakeChatRepository().apply {
            messages.value = listOf(oldestSent, failedOlder)
        }
        val viewModel = ChatViewModel(repository, FakeUserRepository())
        advanceUntilIdle()

        viewModel.onAction(ChatAction.LoadOlderMessages)
        advanceUntilIdle()

        assertEquals(1, repository.olderLoadCount)
    }

    @Test
    fun loadOlderMessages_ignoredWhenMessagesAreEmpty() = runTest(dispatcher) {
        val repository = FakeChatRepository()
        val viewModel = ChatViewModel(repository, FakeUserRepository())
        advanceUntilIdle()

        viewModel.onAction(ChatAction.LoadOlderMessages)
        advanceUntilIdle()

        assertEquals(0, repository.olderLoadCount)
    }

    @Test
    fun loadOlderMessages_ignoredWhenOnlyFailedMessagesExist() = runTest(dispatcher) {
        val failed = message(
            id = "00000000-0000-0000-0000-000000000050",
            createdAt = "2026-08-23T10:50:00Z",
            status = MessageSendStatus.FAILED,
        )
        val repository = FakeChatRepository().apply {
            messages.value = listOf(failed)
        }
        val viewModel = ChatViewModel(repository, FakeUserRepository())
        advanceUntilIdle()

        viewModel.onAction(ChatAction.LoadOlderMessages)
        advanceUntilIdle()

        assertEquals(0, repository.olderLoadCount)
        assertTrue(viewModel.uiState.value.hasMoreOlderMessages)
        assertFalse(viewModel.uiState.value.isLoadingOlder)
    }

    @Test
    fun loadOlderMessages_ignoresConcurrentRequestsWhileLoading() = runTest(dispatcher) {
        val existing = message("00000000-0000-0000-0000-000000000001")
        val repository = FakeChatRepository().apply {
            messages.value = listOf(existing)
            olderGate = CompletableDeferred()
        }
        val viewModel = ChatViewModel(repository, FakeUserRepository())
        advanceUntilIdle()

        viewModel.onAction(ChatAction.LoadOlderMessages)
        dispatcher.scheduler.runCurrent()
        viewModel.onAction(ChatAction.LoadOlderMessages)

        assertEquals(1, repository.olderLoadCount)
        assertTrue(viewModel.uiState.value.isLoadingOlder)

        repository.olderGate!!.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoadingOlder)
        assertEquals(1, repository.olderLoadCount)
    }

    @Test
    fun loadOlderMessages_shortPageMarksHistoryExhausted() = runTest(dispatcher) {
        val existing = message("00000000-0000-0000-0000-000000000001")
        val repository = FakeChatRepository().apply {
            messages.value = listOf(existing)
            olderPageSize = 7
        }
        val viewModel = ChatViewModel(repository, FakeUserRepository())
        advanceUntilIdle()

        viewModel.onAction(ChatAction.LoadOlderMessages)
        advanceUntilIdle()
        viewModel.onAction(ChatAction.LoadOlderMessages)
        advanceUntilIdle()

        assertEquals(1, repository.olderLoadCount)
        assertFalse(viewModel.uiState.value.hasMoreOlderMessages)
        assertFalse(viewModel.uiState.value.isLoadingOlder)
    }

    @Test
    fun loadOlderMessages_fullPageKeepsHasMoreOlderMessages() = runTest(dispatcher) {
        val existing = message("00000000-0000-0000-0000-000000000001")
        val repository = FakeChatRepository().apply {
            messages.value = listOf(existing)
            olderPageSize = 20
        }
        val viewModel = ChatViewModel(repository, FakeUserRepository())
        advanceUntilIdle()

        viewModel.onAction(ChatAction.LoadOlderMessages)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasMoreOlderMessages)
        assertFalse(viewModel.uiState.value.isLoadingOlder)
    }

    @Test
    fun loadOlderMessages_failureEmitsErrorAndAllowsRetry() = runTest(dispatcher) {
        val existing = message("00000000-0000-0000-0000-000000000001")
        val repository = FakeChatRepository().apply {
            messages.value = listOf(existing)
            olderFailure = IllegalStateException("older page unavailable")
        }
        val viewModel = ChatViewModel(repository, FakeUserRepository())
        advanceUntilIdle()

        viewModel.onAction(ChatAction.LoadOlderMessages)
        advanceUntilIdle()

        assertEquals(
            ChatEvent.ShowError("older page unavailable"),
            viewModel.events.first(),
        )
        assertTrue(viewModel.uiState.value.hasMoreOlderMessages)
        assertFalse(viewModel.uiState.value.isLoadingOlder)
        assertEquals(listOf(existing), viewModel.uiState.value.messages)
    }
}

private class FakeChatRepository : ChatRepository {
    val messages = MutableStateFlow<List<Message>>(emptyList())
    val sentTexts = mutableListOf<String>()
    val sentMedia = mutableListOf<SentMediaMessage>()
    val retriedMessageIds = mutableListOf<UUID>()
    var sendFailure: Exception? = null
    var mediaSendFailure: Exception? = null
    var loadLatestCount = 0
    var loadLatestFailure: Exception? = null
    var startRealtimeCount = 0
    var startRealtimeFailure: Exception? = null
    var olderLoadCount = 0
    var olderPageSize = 0
    var olderFailure: Exception? = null
    var olderGate: CompletableDeferred<Unit>? = null

    override fun observeMessages(): Flow<List<Message>> = messages

    override suspend fun sendTextMessage(text: String) {
        sentTexts += text
        sendFailure?.let { throw it }
    }

    override suspend fun retryMessage(messageId: UUID) {
        retriedMessageIds += messageId
    }

    override suspend fun cancelOutgoingSend(messageId: UUID) = unused()

    override suspend fun loadLatestMessages(limit: Int) {
        loadLatestCount += 1
        loadLatestFailure?.let { throw it }
    }
    override suspend fun loadOlderMessages(limit: Int): Int {
        olderLoadCount += 1
        olderGate?.await()
        olderFailure?.let { throw it }
        return olderPageSize
    }
    override suspend fun sendMediaMessage(media: List<PendingMedia>, text: String?) {
        sentMedia += SentMediaMessage(media = media, text = text)
        mediaSendFailure?.let { throw it }
    }
    override suspend fun retryMediaItem(messageId: UUID, mediaId: UUID) = unused()
    override suspend fun startRealtimeSync() {
        startRealtimeCount += 1
        startRealtimeFailure?.let { throw it }
    }
    override suspend fun stopRealtimeSync() = unused()
}

private data class SentMediaMessage(
    val media: List<PendingMedia>,
    val text: String?,
)

private fun attachment(
    uri: String,
    mediaType: MediaType,
    mimeType: String,
): ComposerAttachment =
    ComposerAttachment(
        uri = uri,
        mediaType = mediaType,
        mimeType = mimeType,
    )

private class FakeUserRepository(
    private val currentUserId: UUID = UUID.fromString("33eed91f-846c-49c8-851d-bca519b01432"),
) : UserRepository {
    override suspend fun getCurrentUserId(): UUID = currentUserId

    override suspend fun getUser(userId: UUID): User? = unused()

    override fun observeUser(userId: UUID): Flow<User?> = unused()

    override suspend fun upsertUser(user: User) = unused()

    override suspend fun uploadProfileImage(
        bytes: ByteArray,
        mimeType: String,
        fileExtension: String,
    ) = unused()
}

private fun unused(): Nothing = error("Not used by this test.")
