package com.example.chatapptask.data.chat.repository

import com.example.chatapptask.core.common.identity.UserIdentityStore
import com.example.chatapptask.core.domain.model.MAX_MEDIA_ITEM_BYTES
import com.example.chatapptask.core.domain.model.MediaType
import com.example.chatapptask.core.domain.model.MediaUploadStatus
import com.example.chatapptask.core.domain.model.Message
import com.example.chatapptask.core.domain.model.MessageMedia
import com.example.chatapptask.core.domain.model.MessageSendStatus
import com.example.chatapptask.core.domain.model.PendingMedia
import com.example.chatapptask.core.domain.model.User
import com.example.chatapptask.data.chat.local.ChatLocalDataSource
import com.example.chatapptask.data.chat.local.OutgoingMediaStore
import com.example.chatapptask.data.chat.remote.ChatRemoteDataSource
import com.example.chatapptask.data.chat.worker.MediaMessageScheduleReason
import com.example.chatapptask.data.chat.worker.MediaMessageSendScheduler
import com.example.chatapptask.data.chat.worker.TextMessageSendScheduler
import com.example.chatapptask.data.chat.worker.TextMessageScheduleReason
import androidx.work.ExistingWorkPolicy
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
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
        assertEquals(
            listOf(ScheduledMessage(message.id, TextMessageScheduleReason.INITIAL)),
            scheduler.messages,
        )
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
        assertEquals(
            listOf(ScheduledMessage(message.id, TextMessageScheduleReason.INITIAL)),
            scheduler.messages,
        )
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
        val mediaScheduler = RecordingMediaMessageSendScheduler(events)
        val repository = createRepository(
            local,
            remote,
            scheduler,
            mediaMessageSendScheduler = mediaScheduler,
        )

        repository.retryMessage(messageId)

        assertEquals(0, local.upsertCount)
        assertEquals(
            listOf(ScheduledMessage(messageId, TextMessageScheduleReason.MANUAL_RETRY)),
            scheduler.messages,
        )
        assertTrue(mediaScheduler.messages.isEmpty())
        assertTrue(remote.messageIds.isEmpty())
        assertEquals(listOf("scheduler:enqueue"), events)
    }

    @Test
    fun cancelOutgoingSend_cancelsWorkAndMarksSendingFailed_withoutRemoteCall() = runBlocking {
        val events = mutableListOf<String>()
        val messageId = UUID.fromString("7b1f9c0e-2d44-4a1b-9c3e-0f8a2b6d4e11")
        val local = RecordingLocalDataSource(events).apply {
            seedMessage(persistedTextMessage(messageId, MessageSendStatus.SENDING))
        }
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt)
        val scheduler = RecordingTextMessageSendScheduler(events)
        val repository = createRepository(local, remote, scheduler)

        repository.cancelOutgoingSend(messageId)

        assertEquals(listOf(messageId), scheduler.cancelled)
        assertEquals(MessageSendStatus.FAILED, requireNotNull(local.currentMessage).sendStatus)
        assertTrue(remote.messageIds.isEmpty())
        assertEquals(0, local.upsertCount)
        assertEquals(listOf("scheduler:cancel", "local:FAILED"), events)
        assertEquals("Send cancelled.", local.stateUpdates.last().lastError)
    }

    @Test
    fun cancelOutgoingSend_whenAlreadySent_cancelsWorkButLeavesSent() = runBlocking {
        val events = mutableListOf<String>()
        val messageId = UUID.fromString("0c9e4b77-8a21-4d5f-b3c1-6e5a9f2d8c40")
        val local = RecordingLocalDataSource(events).apply {
            seedMessage(persistedTextMessage(messageId, MessageSendStatus.SENT))
        }
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt)
        val scheduler = RecordingTextMessageSendScheduler(events)
        val repository = createRepository(local, remote, scheduler)

        repository.cancelOutgoingSend(messageId)

        assertEquals(listOf(messageId), scheduler.cancelled)
        assertEquals(MessageSendStatus.SENT, requireNotNull(local.currentMessage).sendStatus)
        assertEquals(listOf("scheduler:cancel"), events)
    }

    @Test
    fun retryMessage_missingLocalMessage_failsWithoutScheduling() = runBlocking {
        val messageId = UUID.fromString("a4558744-b5f6-4ca3-8f81-9ba9750565ea")
        val local = RecordingLocalDataSource(mutableListOf())
        val remote = RecordingRemoteDataSource(mutableListOf(), serverCreatedAt, serverUpdatedAt)
        val scheduler = RecordingTextMessageSendScheduler(mutableListOf())
        val mediaScheduler = RecordingMediaMessageSendScheduler(mutableListOf())
        val repository = createRepository(local, remote, scheduler, mediaMessageSendScheduler = mediaScheduler)
        var thrown: Throwable? = null

        try {
            repository.retryMessage(messageId)
        } catch (exception: Throwable) {
            thrown = exception
        }

        assertTrue(thrown is PersistedTextMessageNotFoundException)
        assertTrue(scheduler.messages.isEmpty())
        assertTrue(mediaScheduler.messages.isEmpty())
        assertTrue(remote.messageIds.isEmpty())
        assertEquals(0, local.upsertCount)
    }

    @Test
    fun retryMessage_media_schedulesExistingIdWithReplace_withoutCreatingOrUploading() = runBlocking {
        val events = mutableListOf<String>()
        val messageId = UUID.fromString("dc4e6f23-5017-44de-bdf9-45c737a2dcc9")
        val uploadedId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01")
        val pendingId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01")
        val uploadedPath = "$messageId/$uploadedId.jpg"
        val originalMedia = listOf(
            localMedia(
                messageId,
                uploadedId,
                0,
                storagePath = uploadedPath,
                uploadStatus = MediaUploadStatus.UPLOADED,
            ),
            localMedia(
                messageId,
                pendingId,
                1,
                uploadStatus = MediaUploadStatus.PENDING,
            ),
        )
        val local = RecordingLocalDataSource(events).apply {
            seedMessage(
                persistedMediaMessage(
                    messageId,
                    originalMedia,
                    status = MessageSendStatus.FAILED,
                ),
            )
        }
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt)
        val textScheduler = RecordingTextMessageSendScheduler(events)
        val mediaScheduler = RecordingMediaMessageSendScheduler(events)
        val repository = createRepository(
            local,
            remote,
            textScheduler,
            mediaMessageSendScheduler = mediaScheduler,
        )

        repository.retryMessage(messageId)

        assertEquals(0, local.upsertCount)
        assertTrue(textScheduler.messages.isEmpty())
        assertEquals(
            listOf(ScheduledMediaMessage(messageId, MediaMessageScheduleReason.MANUAL_RETRY)),
            mediaScheduler.messages,
        )
        assertEquals(ExistingWorkPolicy.REPLACE, mediaScheduler.messages.single().reason.existingWorkPolicy)
        assertTrue(remote.messageIds.isEmpty())
        assertTrue(remote.uploadedMedia.isEmpty())
        assertTrue(remote.createdMediaMessages.isEmpty())
        assertEquals(listOf("media-scheduler:enqueue"), events)
        assertEquals(originalMedia, requireNotNull(local.currentMessage).media)
        assertEquals(uploadedPath, requireNotNull(local.currentMessage).media[0].storagePath)
        assertEquals(uploadedId, requireNotNull(local.currentMessage).media[0].id)
        assertEquals(pendingId, requireNotNull(local.currentMessage).media[1].id)
        assertEquals(MediaUploadStatus.UPLOADED, requireNotNull(local.currentMessage).media[0].uploadStatus)
        assertEquals(MessageSendStatus.FAILED, requireNotNull(local.currentMessage).sendStatus)
    }

    @Test
    fun retryMessage_media_doesNotEnterTextOnlyValidation() = runBlocking {
        val messageId = UUID.fromString("c1d2e3f4-5017-44de-bdf9-45c737a2dca1")
        val mediaId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddd01")
        val local = RecordingLocalDataSource(mutableListOf()).apply {
            seedMessage(
                persistedMediaMessage(
                    messageId,
                    listOf(localMedia(messageId, mediaId, 0)),
                    status = MessageSendStatus.FAILED,
                ),
            )
        }
        val textScheduler = RecordingTextMessageSendScheduler(mutableListOf())
        val mediaScheduler = RecordingMediaMessageSendScheduler(mutableListOf())
        val repository = createRepository(
            local,
            RecordingRemoteDataSource(mutableListOf(), serverCreatedAt, serverUpdatedAt),
            textScheduler,
            mediaMessageSendScheduler = mediaScheduler,
        )
        var thrown: Throwable? = null

        try {
            repository.retryMessage(messageId)
        } catch (exception: Throwable) {
            thrown = exception
        }

        assertEquals(null, thrown)
        assertTrue(thrown !is PersistedMessageIsNotTextException)
        assertTrue(textScheduler.messages.isEmpty())
        assertEquals(
            listOf(ScheduledMediaMessage(messageId, MediaMessageScheduleReason.MANUAL_RETRY)),
            mediaScheduler.messages,
        )
    }

    @Test
    fun sendMediaMessage_singleItem_persistsSendingWithPendingDurableCopy() = runBlocking {
        val events = mutableListOf<String>()
        val local = RecordingLocalDataSource(events)
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt)
        val scheduler = RecordingTextMessageSendScheduler(events)
        val store = RecordingOutgoingMediaStore(events)
        val mediaScheduler = RecordingMediaMessageSendScheduler(events)
        val repository = createRepository(local, remote, scheduler, store, mediaScheduler)
        val pending = pendingMedia("content://picker/one", mimeType = "image/jpeg")

        repository.sendMediaMessage(listOf(pending), text = "caption")

        val message = requireNotNull(local.persistedMessage)
        val media = message.media.single()
        assertEquals(1, local.upsertCount)
        assertEquals(MessageSendStatus.SENDING, message.sendStatus)
        assertEquals(senderId, message.senderId)
        assertEquals("caption", message.textContent)
        assertEquals(MediaUploadStatus.PENDING, media.uploadStatus)
        assertEquals(null, media.storagePath)
        assertEquals(0, media.position)
        assertEquals(pending.mimeType, media.mimeType)
        assertEquals(pending.mediaType, media.mediaType)
        assertEquals(pending.sizeBytes, media.sizeBytes)
        assertEquals(pending.width, media.width)
        assertEquals(pending.height, media.height)
        assertEquals(store.copied.single().durableUri, media.localUri)
        assertEquals(message.id, store.copied.single().messageId)
        assertEquals(media.id, store.copied.single().mediaId)
        assertTrue(scheduler.messages.isEmpty())
        assertEquals(
            listOf(ScheduledMediaMessage(message.id, MediaMessageScheduleReason.INITIAL)),
            mediaScheduler.messages,
        )
        assertTrue(remote.messageIds.isEmpty())
        assertEquals(listOf("store:copy", "local:SENDING", "media-scheduler:enqueue"), events)
    }

    @Test
    fun sendMediaMessage_multipleItems_preserveOrderAndUniqueMediaIds() = runBlocking {
        val store = RecordingOutgoingMediaStore(mutableListOf())
        val mediaScheduler = RecordingMediaMessageSendScheduler(mutableListOf())
        val local = RecordingLocalDataSource(mutableListOf())
        val repository = createRepository(
            local,
            RecordingRemoteDataSource(mutableListOf(), serverCreatedAt, serverUpdatedAt),
            RecordingTextMessageSendScheduler(mutableListOf()),
            store,
            mediaScheduler,
        )
        val pending = listOf(
            pendingMedia("content://picker/a", mimeType = "image/png"),
            pendingMedia("content://picker/b", mimeType = "video/mp4", type = MediaType.VIDEO),
            pendingMedia("content://picker/c", mimeType = "image/webp"),
        )

        repository.sendMediaMessage(pending)

        val message = requireNotNull(local.persistedMessage)
        assertEquals(listOf(0, 1, 2), message.media.map(MessageMedia::position))
        assertEquals(
            listOf("content://picker/a", "content://picker/b", "content://picker/c"),
            store.copied.map(CopiedOutgoingMedia::sourceUri),
        )
        assertEquals(3, message.media.map(MessageMedia::id).toSet().size)
        assertTrue(message.media.none { item -> item.id == message.id })
        assertEquals(
            store.copied.map(CopiedOutgoingMedia::durableUri),
            message.media.map(MessageMedia::localUri),
        )
        assertTrue(message.media.all { item -> item.uploadStatus == MediaUploadStatus.PENDING })
        assertTrue(message.media.all { item -> item.storagePath == null })
        assertEquals(
            listOf(ScheduledMediaMessage(message.id, MediaMessageScheduleReason.INITIAL)),
            mediaScheduler.messages,
        )
    }

    @Test
    fun sendMediaMessage_tenItems_accepted() = runBlocking {
        val local = RecordingLocalDataSource(mutableListOf())
        val store = RecordingOutgoingMediaStore(mutableListOf())
        val mediaScheduler = RecordingMediaMessageSendScheduler(mutableListOf())
        val repository = createRepository(
            local,
            RecordingRemoteDataSource(mutableListOf(), serverCreatedAt, serverUpdatedAt),
            RecordingTextMessageSendScheduler(mutableListOf()),
            store,
            mediaScheduler,
        )

        repository.sendMediaMessage(
            List(10) { index -> pendingMedia("content://picker/$index") },
        )

        val message = requireNotNull(local.persistedMessage)
        assertEquals(10, message.media.size)
        assertEquals(10, store.copied.size)
        assertEquals((0..9).toList(), message.media.map(MessageMedia::position))
        assertEquals(10, message.media.map(MessageMedia::id).distinct().size)
        assertEquals(1, mediaScheduler.messages.size)
        assertEquals(message.id, mediaScheduler.messages.single().messageId)
    }

    @Test
    fun sendMediaMessage_moreThanTenItems_rejectedWithoutCopyOrPersist() = runBlocking {
        val events = mutableListOf<String>()
        val local = RecordingLocalDataSource(events)
        val store = RecordingOutgoingMediaStore(events)
        val mediaScheduler = RecordingMediaMessageSendScheduler(events)
        val repository = createRepository(
            local,
            RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt),
            RecordingTextMessageSendScheduler(events),
            store,
            mediaScheduler,
        )
        var thrown: Throwable? = null

        try {
            repository.sendMediaMessage(
                List(11) { index -> pendingMedia("content://picker/$index") },
            )
        } catch (exception: Throwable) {
            thrown = exception
        }

        assertTrue(thrown is IllegalArgumentException)
        assertEquals(0, local.upsertCount)
        assertTrue(store.copied.isEmpty())
        assertTrue(mediaScheduler.messages.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun sendMediaMessage_oversizedItem_rejectedWithoutCopyOrPersist() = runBlocking {
        val local = RecordingLocalDataSource(mutableListOf())
        val store = RecordingOutgoingMediaStore(mutableListOf())
        val mediaScheduler = RecordingMediaMessageSendScheduler(mutableListOf())
        val repository = createRepository(
            local,
            RecordingRemoteDataSource(mutableListOf(), serverCreatedAt, serverUpdatedAt),
            RecordingTextMessageSendScheduler(mutableListOf()),
            store,
            mediaScheduler,
        )
        var thrown: Throwable? = null

        try {
            repository.sendMediaMessage(
                listOf(pendingMedia("content://picker/huge", sizeBytes = MAX_MEDIA_ITEM_BYTES + 1)),
            )
        } catch (exception: Throwable) {
            thrown = exception
        }

        assertTrue(thrown is IllegalArgumentException)
        assertEquals("Each photo or video must be 50 MB or smaller.", thrown?.message)
        assertEquals(0, local.upsertCount)
        assertTrue(store.copied.isEmpty())
        assertTrue(mediaScheduler.messages.isEmpty())
    }

    @Test
    fun sendMediaMessage_exactlyAtLimit_isAccepted() = runBlocking {
        val events = mutableListOf<String>()
        val local = RecordingLocalDataSource(events)
        val store = RecordingOutgoingMediaStore(events)
        val mediaScheduler = RecordingMediaMessageSendScheduler(events)
        val repository = createRepository(
            local,
            RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt),
            RecordingTextMessageSendScheduler(events),
            store,
            mediaScheduler,
        )

        repository.sendMediaMessage(
            listOf(pendingMedia("content://picker/limit", sizeBytes = MAX_MEDIA_ITEM_BYTES)),
        )

        assertEquals(1, local.upsertCount)
        assertEquals(1, store.copied.size)
        assertEquals(1, mediaScheduler.messages.size)
        assertEquals(MAX_MEDIA_ITEM_BYTES, requireNotNull(local.persistedMessage).media.single().sizeBytes)
    }

    @Test
    fun sendMediaMessage_belowLimit_isAccepted() = runBlocking {
        val local = RecordingLocalDataSource(mutableListOf())
        val store = RecordingOutgoingMediaStore(mutableListOf())
        val mediaScheduler = RecordingMediaMessageSendScheduler(mutableListOf())
        val repository = createRepository(
            local,
            RecordingRemoteDataSource(mutableListOf(), serverCreatedAt, serverUpdatedAt),
            RecordingTextMessageSendScheduler(mutableListOf()),
            store,
            mediaScheduler,
        )

        repository.sendMediaMessage(
            listOf(pendingMedia("content://picker/image.jpg", sizeBytes = MAX_MEDIA_ITEM_BYTES - 1)),
        )

        assertEquals(1, local.upsertCount)
        assertEquals(1, store.copied.size)
        assertEquals(1, mediaScheduler.messages.size)
    }

    @Test
    fun sendMediaMessage_copiedFileOversized_rejectedWithoutPersist() = runBlocking {
        val local = RecordingLocalDataSource(mutableListOf())
        val store = RecordingOutgoingMediaStore(mutableListOf()).apply {
            defaultCopySizeBytes = MAX_MEDIA_ITEM_BYTES + 1
        }
        val mediaScheduler = RecordingMediaMessageSendScheduler(mutableListOf())
        val repository = createRepository(
            local,
            RecordingRemoteDataSource(mutableListOf(), serverCreatedAt, serverUpdatedAt),
            RecordingTextMessageSendScheduler(mutableListOf()),
            store,
            mediaScheduler,
        )
        var thrown: Throwable? = null

        try {
            repository.sendMediaMessage(
                listOf(pendingMedia("content://picker/unknown-size", sizeBytes = null)),
            )
        } catch (exception: Throwable) {
            thrown = exception
        }

        assertTrue(thrown is IllegalArgumentException)
        assertEquals("Each photo or video must be 50 MB or smaller.", thrown?.message)
        assertEquals(0, local.upsertCount)
        assertTrue(store.copied.isEmpty())
        assertEquals(1, store.deletedMessageIds.size)
        assertTrue(mediaScheduler.messages.isEmpty())
    }

    @Test
    fun sendMediaMessage_emptyList_rejectedWithoutCopyOrPersist() = runBlocking {
        val local = RecordingLocalDataSource(mutableListOf())
        val store = RecordingOutgoingMediaStore(mutableListOf())
        val mediaScheduler = RecordingMediaMessageSendScheduler(mutableListOf())
        val repository = createRepository(
            local,
            RecordingRemoteDataSource(mutableListOf(), serverCreatedAt, serverUpdatedAt),
            RecordingTextMessageSendScheduler(mutableListOf()),
            store,
            mediaScheduler,
        )
        var thrown: Throwable? = null

        try {
            repository.sendMediaMessage(emptyList())
        } catch (exception: Throwable) {
            thrown = exception
        }

        assertTrue(thrown is IllegalArgumentException)
        assertEquals(0, local.upsertCount)
        assertTrue(store.copied.isEmpty())
        assertTrue(mediaScheduler.messages.isEmpty())
    }

    @Test
    fun sendMediaMessage_copyFailure_deletesCopiedFilesAndDoesNotPersist() = runBlocking {
        val events = mutableListOf<String>()
        val local = RecordingLocalDataSource(events)
        val store = RecordingOutgoingMediaStore(events, failOnCopyIndex = 1)
        val mediaScheduler = RecordingMediaMessageSendScheduler(events)
        val repository = createRepository(
            local,
            RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt),
            RecordingTextMessageSendScheduler(events),
            store,
            mediaScheduler,
        )
        var thrown: Throwable? = null

        try {
            repository.sendMediaMessage(
                listOf(
                    pendingMedia("content://picker/a"),
                    pendingMedia("content://picker/b"),
                ),
            )
        } catch (exception: Throwable) {
            thrown = exception
        }

        assertEquals("copy failed", thrown?.message)
        assertEquals(0, local.upsertCount)
        assertTrue(store.copied.isEmpty())
        assertEquals(1, store.deletedMessageIds.size)
        assertEquals(listOf(store.deletedMessageIds.single()), local.deletedMessageIds)
        assertTrue(mediaScheduler.messages.isEmpty())
        assertEquals(listOf("store:copy", "store:copy-fail", "store:delete", "local:delete"), events)
    }

    @Test
    fun sendMediaMessage_persistFailure_deletesCopiedFilesAndMessageRow() = runBlocking {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("room unavailable")
        val local = RecordingLocalDataSource(events, upsertFailure = failure)
        val store = RecordingOutgoingMediaStore(events)
        val mediaScheduler = RecordingMediaMessageSendScheduler(events)
        val repository = createRepository(
            local,
            RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt),
            RecordingTextMessageSendScheduler(events),
            store,
            mediaScheduler,
        )
        var thrown: Throwable? = null

        try {
            repository.sendMediaMessage(listOf(pendingMedia("content://picker/a")))
        } catch (exception: Throwable) {
            thrown = exception
        }

        assertSame(failure, thrown)
        assertEquals(1, local.upsertCount)
        assertEquals(null, local.persistedMessage)
        assertEquals(store.deletedMessageIds, local.deletedMessageIds)
        assertTrue(store.copied.isEmpty())
        assertTrue(mediaScheduler.messages.isEmpty())
        assertEquals(listOf("store:copy", "local:SENDING", "store:delete", "local:delete"), events)
    }

    @Test
    fun sendMediaMessage_schedulingFailure_keepsMessageAndMarksFailed() = runBlocking {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("media scheduler unavailable")
        val local = RecordingLocalDataSource(events)
        val store = RecordingOutgoingMediaStore(events)
        val mediaScheduler = RecordingMediaMessageSendScheduler(events, failure)
        val repository = createRepository(
            local,
            RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt),
            RecordingTextMessageSendScheduler(events),
            store,
            mediaScheduler,
        )
        var thrown: Throwable? = null

        try {
            repository.sendMediaMessage(listOf(pendingMedia("content://picker/a")))
        } catch (exception: Throwable) {
            thrown = exception
        }

        val message = requireNotNull(local.currentMessage)
        assertSame(failure, thrown)
        assertEquals(1, local.upsertCount)
        assertEquals(MessageSendStatus.FAILED, message.sendStatus)
        assertEquals("media scheduler unavailable", local.stateUpdates.single().lastError)
        assertEquals(
            listOf(ScheduledMediaMessage(requireNotNull(local.persistedMessage).id, MediaMessageScheduleReason.INITIAL)),
            mediaScheduler.messages,
        )
        assertEquals(
            listOf("store:copy", "local:SENDING", "media-scheduler:enqueue", "local:FAILED"),
            events,
        )
    }

    @Test
    fun sendPersistedMediaMessage_uploadsThenCreatesRemoteAndReconcilesSent() = runBlocking {
        val events = mutableListOf<String>()
        val progress = mutableListOf<Pair<Int, Int>>()
        val messageId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        val mediaId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
        val store = RecordingOutgoingMediaStore(events)
        val local = RecordingLocalDataSource(events).apply {
            seedMessage(
                persistedMediaMessage(messageId, listOf(localMedia(messageId, mediaId, 0))),
                attemptCount = 0,
            )
        }
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt)
        val repository = createRepository(
            local,
            remote,
            RecordingTextMessageSendScheduler(events),
            store,
        )

        repository.sendPersistedMediaMessage(messageId) { current, total ->
            progress += current to total
        }

        val message = requireNotNull(local.currentMessage)
        assertEquals(1, local.sendAttemptCount)
        assertEquals(MessageSendStatus.SENT, message.sendStatus)
        assertEquals(serverCreatedAt, message.createdAt)
        assertEquals(listOf(mediaId), remote.uploadedMedia.map { item -> item.mediaId })
        assertEquals("$messageId/$mediaId.jpg", remote.uploadedMedia.single().storagePath)
        assertEquals(listOf(mediaId), remote.createdMediaMessages.single().media.map { item -> item.id })
        assertEquals(MediaUploadStatus.UPLOADED, message.media.single().uploadStatus)
        assertEquals("$messageId/$mediaId.jpg", message.media.single().storagePath)
        assertEquals(listOf(1 to 1), progress)
        assertEquals(listOf(messageId), store.deletedMessageIds)
        assertEquals(setOf(messageId), local.messagesById.keys)
        assertEquals(
            listOf(
                "local:SENDING",
                "local:media-UPLOADING",
                "remote:upload",
                "local:media-UPLOADED",
                "remote:getMessage",
                "remote:createMedia",
                "local:SENT",
                "store:delete",
            ),
            events,
        )
    }

    @Test
    fun sendPersistedMediaMessage_textOnlyMessage_failsWithoutAttempt() = runBlocking {
        val messageId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
        val local = RecordingLocalDataSource(mutableListOf()).apply {
            seedMessage(persistedTextMessage(messageId, MessageSendStatus.SENDING), attemptCount = 0)
        }
        val repository = createRepository(
            local,
            RecordingRemoteDataSource(mutableListOf(), serverCreatedAt, serverUpdatedAt),
            RecordingTextMessageSendScheduler(mutableListOf()),
        )
        var thrown: Throwable? = null

        try {
            repository.sendPersistedMediaMessage(messageId)
        } catch (exception: Throwable) {
            thrown = exception
        }

        assertTrue(thrown is PersistedMessageIsNotMediaException)
        assertEquals(0, local.sendAttemptCount)
    }

    @Test
    fun sendPersistedMediaMessage_missingMessage_failsDeterministically() = runBlocking {
        val messageId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd")
        val local = RecordingLocalDataSource(mutableListOf())
        val repository = createRepository(
            local,
            RecordingRemoteDataSource(mutableListOf(), serverCreatedAt, serverUpdatedAt),
            RecordingTextMessageSendScheduler(mutableListOf()),
        )
        var thrown: Throwable? = null

        try {
            repository.sendPersistedMediaMessage(messageId)
        } catch (exception: Throwable) {
            thrown = exception
        }

        assertTrue(thrown is PersistedMediaMessageNotFoundException)
        assertEquals(0, local.sendAttemptCount)
    }

    @Test
    fun sendPersistedMediaMessage_invalidAttachmentCount_failsWithoutAttempt() = runBlocking {
        val messageId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee")
        val media = List(11) { index ->
            localMedia(
                messageId,
                UUID.fromString("00000000-0000-0000-0000-0000000000${index.toString().padStart(2, '0')}"),
                index,
            )
        }
        val local = RecordingLocalDataSource(mutableListOf()).apply {
            seedMessage(persistedMediaMessage(messageId, media), attemptCount = 0)
        }
        val repository = createRepository(
            local,
            RecordingRemoteDataSource(mutableListOf(), serverCreatedAt, serverUpdatedAt),
            RecordingTextMessageSendScheduler(mutableListOf()),
        )
        var thrown: Throwable? = null

        try {
            repository.sendPersistedMediaMessage(messageId)
        } catch (exception: Throwable) {
            thrown = exception
        }

        assertTrue(thrown is PersistedMediaMessageInvalidException)
        assertEquals(0, local.sendAttemptCount)
    }

    @Test
    fun sendPersistedMediaMessage_missingDurableLocalFile_failsWithoutAttempt() = runBlocking {
        val messageId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")
        val mediaId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val localUri = "file:///outgoing-media/$messageId/$mediaId"
        val store = RecordingOutgoingMediaStore(mutableListOf()).apply {
            unreadableUris += localUri
        }
        val local = RecordingLocalDataSource(mutableListOf()).apply {
            seedMessage(
                persistedMediaMessage(
                    messageId,
                    listOf(localMedia(messageId, mediaId, 0, localUri = localUri)),
                ),
                attemptCount = 0,
            )
        }
        val repository = createRepository(
            local,
            RecordingRemoteDataSource(mutableListOf(), serverCreatedAt, serverUpdatedAt),
            RecordingTextMessageSendScheduler(mutableListOf()),
            store,
        )
        var thrown: Throwable? = null

        try {
            repository.sendPersistedMediaMessage(messageId)
        } catch (exception: Throwable) {
            thrown = exception
        }

        assertTrue(thrown is PersistedMediaLocalFileMissingException)
        assertEquals(0, local.sendAttemptCount)
    }

    @Test
    fun sendPersistedMediaMessage_oversizedCopy_failsPermanentlyWithoutUpload() = runBlocking {
        val messageId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa20")
        val mediaId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb20")
        val localUri = "file:///outgoing-media/$messageId/$mediaId.jpg"
        val store = RecordingOutgoingMediaStore(mutableListOf()).apply {
            copySizeByUri[localUri] = MAX_MEDIA_ITEM_BYTES + 1
        }
        val remote = RecordingRemoteDataSource(mutableListOf(), serverCreatedAt, serverUpdatedAt)
        val local = RecordingLocalDataSource(mutableListOf()).apply {
            seedMessage(
                persistedMediaMessage(
                    messageId,
                    listOf(localMedia(messageId, mediaId, 0, localUri = localUri)),
                    status = MessageSendStatus.SENDING,
                ),
                attemptCount = 3,
            )
        }
        val repository = createRepository(
            local,
            remote,
            RecordingTextMessageSendScheduler(mutableListOf()),
            store,
        )
        var thrown: Throwable? = null

        try {
            repository.sendPersistedMediaMessage(messageId)
        } catch (exception: Throwable) {
            thrown = exception
        }

        assertTrue(thrown is PermanentMediaUploadException)
        assertEquals("Each photo or video must be 50 MB or smaller.", thrown?.message)
        assertTrue(remote.uploadedMedia.isEmpty())
        assertTrue(remote.createdMediaMessages.isEmpty())
        assertEquals(3, local.sendAttemptCount)
        assertEquals(MessageSendStatus.FAILED, requireNotNull(local.currentMessage).sendStatus)
        assertEquals("Each photo or video must be 50 MB or smaller.", local.stateUpdates.last().lastError)
        assertEquals(MediaUploadStatus.PENDING, requireNotNull(local.currentMessage).media.single().uploadStatus)
    }

    @Test
    fun sendPersistedMediaMessage_payloadTooLarge_isPermanentFailure() = runBlocking {
        val messageId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa21")
        val mediaId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb21")
        val local = RecordingLocalDataSource(mutableListOf()).apply {
            seedMessage(
                persistedMediaMessage(
                    messageId,
                    listOf(localMedia(messageId, mediaId, 0)),
                ),
                attemptCount = 0,
            )
        }
        val remote = RecordingRemoteDataSource(mutableListOf(), serverCreatedAt, serverUpdatedAt).apply {
            uploadFailure = IllegalStateException("Payload too large")
        }
        val repository = createRepository(
            local,
            remote,
            RecordingTextMessageSendScheduler(mutableListOf()),
        )
        var thrown: Throwable? = null

        try {
            repository.sendPersistedMediaMessage(messageId)
        } catch (exception: Throwable) {
            thrown = exception
        }

        assertTrue(thrown is PermanentMediaUploadException)
        assertEquals(MessageSendStatus.FAILED, requireNotNull(local.currentMessage).sendStatus)
        assertEquals(MediaUploadStatus.FAILED, requireNotNull(local.currentMessage).media.single().uploadStatus)
    }

    @Test
    fun sendPersistedMediaMessage_uploadsMultipleAttachmentsInPositionOrder() = runBlocking {
        val events = mutableListOf<String>()
        val progress = mutableListOf<Pair<Int, Int>>()
        val messageId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1")
        val firstId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1")
        val secondId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2")
        val local = RecordingLocalDataSource(events).apply {
            seedMessage(
                persistedMediaMessage(
                    messageId,
                    listOf(
                        localMedia(messageId, firstId, 0),
                        localMedia(messageId, secondId, 1),
                    ),
                ),
                attemptCount = 0,
            )
        }
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt)
        val repository = createRepository(
            local,
            remote,
            RecordingTextMessageSendScheduler(events),
        )

        repository.sendPersistedMediaMessage(messageId) { current, total ->
            progress += current to total
        }

        assertEquals(listOf(firstId, secondId), remote.uploadedMedia.map { item -> item.mediaId })
        assertEquals(
            listOf("$messageId/$firstId.jpg", "$messageId/$secondId.jpg"),
            remote.uploadedMedia.map { item -> item.storagePath },
        )
        assertEquals(listOf(1 to 2, 2 to 2), progress)
        assertEquals(MessageSendStatus.SENT, requireNotNull(local.currentMessage).sendStatus)
        assertEquals(1, remote.createdMediaMessages.size)
    }

    @Test
    fun sendPersistedMediaMessage_skipsAlreadyUploadedAttachmentAndReusesPath() = runBlocking {
        val events = mutableListOf<String>()
        val messageId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2")
        val uploadedId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb3")
        val pendingId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb4")
        val existingPath = "$messageId/$uploadedId.jpg"
        val uploadedLocalUri = "file:///outgoing-media/$messageId/$uploadedId.jpg"
        val store = RecordingOutgoingMediaStore(events).apply {
            copySizeByUri[uploadedLocalUri] = MAX_MEDIA_ITEM_BYTES + 1
        }
        val local = RecordingLocalDataSource(events).apply {
            seedMessage(
                persistedMediaMessage(
                    messageId,
                    listOf(
                        localMedia(
                            messageId,
                            uploadedId,
                            0,
                            localUri = uploadedLocalUri,
                            storagePath = existingPath,
                            uploadStatus = MediaUploadStatus.UPLOADED,
                        ),
                        localMedia(messageId, pendingId, 1),
                    ),
                ),
                attemptCount = 0,
            )
        }
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt)
        val repository = createRepository(
            local,
            remote,
            RecordingTextMessageSendScheduler(events),
            store,
        )

        repository.sendPersistedMediaMessage(messageId)

        assertEquals(listOf(pendingId), remote.uploadedMedia.map { item -> item.mediaId })
        val created = remote.createdMediaMessages.single().media
        assertEquals(listOf(uploadedId, pendingId), created.map { item -> item.id })
        assertEquals(existingPath, created[0].storagePath)
        assertEquals("$messageId/$pendingId.jpg", created[1].storagePath)
        assertTrue(requireNotNull(local.currentMessage).media.all { item ->
            item.uploadStatus == MediaUploadStatus.UPLOADED
        })
    }

    @Test
    fun sendPersistedMediaMessage_partialUploadFailure_keepsSuccessfulAttachment() = runBlocking {
        val events = mutableListOf<String>()
        val messageId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3")
        val firstId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb5")
        val secondId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb6")
        val failure = IllegalStateException("storage unavailable")
        val local = RecordingLocalDataSource(events).apply {
            seedMessage(
                persistedMediaMessage(
                    messageId,
                    listOf(
                        localMedia(messageId, firstId, 0),
                        localMedia(messageId, secondId, 1),
                    ),
                ),
                attemptCount = 0,
            )
        }
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt).apply {
            uploadFailureMediaId = secondId
            uploadFailure = failure
        }
        val repository = createRepository(
            local,
            remote,
            RecordingTextMessageSendScheduler(events),
        )
        var thrown: Throwable? = null

        try {
            repository.sendPersistedMediaMessage(messageId)
        } catch (exception: Throwable) {
            thrown = exception
        }

        assertSame(failure, thrown)
        val media = requireNotNull(local.currentMessage).media.sortedBy(MessageMedia::position)
        assertEquals(MediaUploadStatus.UPLOADED, media[0].uploadStatus)
        assertEquals("$messageId/$firstId.jpg", media[0].storagePath)
        assertEquals(MediaUploadStatus.FAILED, media[1].uploadStatus)
        assertEquals(MessageSendStatus.FAILED, requireNotNull(local.currentMessage).sendStatus)
        assertTrue(remote.createdMediaMessages.isEmpty())
        assertEquals(listOf(firstId, secondId), remote.uploadedMedia.map { item -> item.mediaId })
    }

    @Test
    fun sendPersistedMediaMessage_retryAfterPartialFailure_uploadsOnlyRemaining() = runBlocking {
        val events = mutableListOf<String>()
        val messageId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4")
        val firstId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb7")
        val secondId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb8")
        val local = RecordingLocalDataSource(events).apply {
            seedMessage(
                persistedMediaMessage(
                    messageId,
                    listOf(
                        localMedia(messageId, firstId, 0),
                        localMedia(messageId, secondId, 1),
                    ),
                ),
                attemptCount = 0,
            )
        }
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt).apply {
            uploadFailureMediaId = secondId
            uploadFailure = IllegalStateException("storage unavailable")
        }
        val repository = createRepository(
            local,
            remote,
            RecordingTextMessageSendScheduler(events),
        )

        try {
            repository.sendPersistedMediaMessage(messageId)
        } catch (_: IllegalStateException) {
            // Expected first-attempt upload failure.
        }
        remote.uploadFailure = null
        remote.uploadFailureMediaId = null
        repository.sendPersistedMediaMessage(messageId)

        assertEquals(listOf(firstId, secondId, secondId), remote.uploadedMedia.map { item -> item.mediaId })
        assertEquals(1, remote.createdMediaMessages.size)
        assertEquals(
            listOf(firstId, secondId),
            remote.createdMediaMessages.single().media.map { item -> item.id },
        )
        assertEquals(MessageSendStatus.SENT, requireNotNull(local.currentMessage).sendStatus)
        assertTrue(requireNotNull(local.currentMessage).media.all { item ->
            item.uploadStatus == MediaUploadStatus.UPLOADED && item.id in setOf(firstId, secondId)
        })
    }

    @Test
    fun sendPersistedMediaMessage_rpcFailure_preservesUploadedState() = runBlocking {
        val events = mutableListOf<String>()
        val messageId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5")
        val mediaId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb9")
        val failure = IllegalStateException("rpc unavailable")
        val store = RecordingOutgoingMediaStore(events)
        val local = RecordingLocalDataSource(events).apply {
            seedMessage(
                persistedMediaMessage(messageId, listOf(localMedia(messageId, mediaId, 0))),
                attemptCount = 0,
            )
        }
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt).apply {
            createMediaFailure = failure
        }
        val repository = createRepository(
            local,
            remote,
            RecordingTextMessageSendScheduler(events),
            store,
        )
        var thrown: Throwable? = null

        try {
            repository.sendPersistedMediaMessage(messageId)
        } catch (exception: Throwable) {
            thrown = exception
        }

        assertSame(failure, thrown)
        assertEquals(MediaUploadStatus.UPLOADED, requireNotNull(local.currentMessage).media.single().uploadStatus)
        assertEquals("$messageId/$mediaId.jpg", requireNotNull(local.currentMessage).media.single().storagePath)
        assertEquals(MessageSendStatus.FAILED, requireNotNull(local.currentMessage).sendStatus)
        assertTrue(remote.createdMediaMessages.isNotEmpty())
        assertTrue(store.deletedMessageIds.isEmpty())
    }

    @Test
    fun sendPersistedMediaMessage_retryAfterRpcFailure_doesNotReupload() = runBlocking {
        val messageId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa6")
        val mediaId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb10")
        val local = RecordingLocalDataSource(mutableListOf()).apply {
            seedMessage(
                persistedMediaMessage(messageId, listOf(localMedia(messageId, mediaId, 0))),
                attemptCount = 0,
            )
        }
        val remote = RecordingRemoteDataSource(
            mutableListOf(),
            serverCreatedAt,
            serverUpdatedAt,
        ).apply {
            createMediaFailure = IllegalStateException("rpc unavailable")
        }
        val repository = createRepository(
            local,
            remote,
            RecordingTextMessageSendScheduler(mutableListOf()),
        )

        try {
            repository.sendPersistedMediaMessage(messageId)
        } catch (_: IllegalStateException) {
            // Expected first-attempt RPC failure.
        }
        remote.createMediaFailure = null
        repository.sendPersistedMediaMessage(messageId)

        assertEquals(listOf(mediaId), remote.uploadedMedia.map { item -> item.mediaId })
        assertEquals(2, remote.createdMediaMessages.size)
        assertEquals(messageId, remote.createdMediaMessages[0].messageId)
        assertEquals(messageId, remote.createdMediaMessages[1].messageId)
        assertEquals(mediaId, remote.createdMediaMessages[1].media.single().id)
        assertEquals(MessageSendStatus.SENT, requireNotNull(local.currentMessage).sendStatus)
    }

    @Test
    fun sendPersistedMediaMessage_sameUuidsAndDeterministicPathsOnRetry() = runBlocking {
        val messageId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa7")
        val mediaId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb11")
        val local = RecordingLocalDataSource(mutableListOf()).apply {
            seedMessage(
                persistedMediaMessage(messageId, listOf(localMedia(messageId, mediaId, 0))),
                attemptCount = 0,
            )
        }
        val remote = RecordingRemoteDataSource(
            mutableListOf(),
            serverCreatedAt,
            serverUpdatedAt,
        ).apply {
            uploadFailure = IllegalStateException("storage unavailable")
        }
        val repository = createRepository(
            local,
            remote,
            RecordingTextMessageSendScheduler(mutableListOf()),
        )

        try {
            repository.sendPersistedMediaMessage(messageId)
        } catch (_: IllegalStateException) {
            // Expected first-attempt upload failure.
        }
        remote.uploadFailure = null
        repository.sendPersistedMediaMessage(messageId)

        assertEquals(listOf(mediaId, mediaId), remote.uploadedMedia.map { item -> item.mediaId })
        assertTrue(remote.uploadedMedia.all { item -> item.messageId == messageId })
        assertTrue(remote.uploadedMedia.all { item -> item.storagePath == "$messageId/$mediaId.jpg" })
        assertEquals(messageId, requireNotNull(local.currentMessage).id)
        assertEquals(mediaId, requireNotNull(local.currentMessage).media.single().id)
    }

    @Test
    fun sendPersistedMediaMessage_cancellation_stopsRemainingUploadsAndSkipsRpc() = runBlocking {
        val messageId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa8")
        val firstId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb12")
        val secondId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb13")
        val local = RecordingLocalDataSource(mutableListOf()).apply {
            seedMessage(
                persistedMediaMessage(
                    messageId,
                    listOf(
                        localMedia(messageId, firstId, 0),
                        localMedia(messageId, secondId, 1),
                    ),
                ),
                attemptCount = 0,
            )
        }
        val remote = RecordingRemoteDataSource(
            mutableListOf(),
            serverCreatedAt,
            serverUpdatedAt,
        ).apply {
            cancelOnUploadMediaId = secondId
        }
        val repository = createRepository(
            local,
            remote,
            RecordingTextMessageSendScheduler(mutableListOf()),
        )
        var thrown: Throwable? = null

        try {
            repository.sendPersistedMediaMessage(messageId)
        } catch (exception: Throwable) {
            thrown = exception
        }

        assertTrue(thrown is CancellationException)
        val media = requireNotNull(local.currentMessage).media.sortedBy(MessageMedia::position)
        assertEquals(MediaUploadStatus.UPLOADED, media[0].uploadStatus)
        assertEquals(MediaUploadStatus.UPLOADING, media[1].uploadStatus)
        assertEquals(MessageSendStatus.SENDING, requireNotNull(local.currentMessage).sendStatus)
        assertTrue(remote.createdMediaMessages.isEmpty())
        assertEquals(listOf(firstId, secondId), remote.uploadedMedia.map { item -> item.mediaId })
    }

    @Test
    fun sendPersistedMediaMessage_existingRemoteMessage_skipsCreateAndReconciles() = runBlocking {
        val messageId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa9")
        val mediaId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb14")
        val local = RecordingLocalDataSource(mutableListOf()).apply {
            seedMessage(
                persistedMediaMessage(
                    messageId,
                    listOf(
                        localMedia(
                            messageId,
                            mediaId,
                            0,
                            storagePath = "$messageId/$mediaId.jpg",
                            uploadStatus = MediaUploadStatus.UPLOADED,
                        ),
                    ),
                ),
                attemptCount = 0,
            )
        }
        val remote = RecordingRemoteDataSource(
            mutableListOf(),
            serverCreatedAt,
            serverUpdatedAt,
        ).apply {
            completeMessagesById[messageId] = remoteMessage(
                id = messageId,
                senderId = senderId,
                text = null,
                status = MessageSendStatus.SENT,
                media = listOf(
                    localMedia(
                        messageId,
                        mediaId,
                        0,
                        storagePath = "$messageId/$mediaId.jpg",
                        uploadStatus = MediaUploadStatus.UPLOADED,
                    ),
                ),
            )
        }
        val repository = createRepository(
            local,
            remote,
            RecordingTextMessageSendScheduler(mutableListOf()),
        )

        repository.sendPersistedMediaMessage(messageId)

        assertTrue(remote.uploadedMedia.isEmpty())
        assertTrue(remote.createdMediaMessages.isEmpty())
        assertEquals(MessageSendStatus.SENT, requireNotNull(local.currentMessage).sendStatus)
    }

    @Test
    fun startRealtimeSync_existingOptimisticMedia_doesNotReplaceLocalMediaFields() = runBlocking {
        val events = mutableListOf<String>()
        val messageId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa10")
        val mediaId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb15")
        val localUri = "file:///outgoing-media/$messageId/$mediaId.jpg"
        val sender = remoteUser(senderId)
        val local = RecordingLocalDataSource(events).apply {
            seedUser(sender)
            seedMessage(
                persistedMediaMessage(
                    messageId,
                    listOf(localMedia(messageId, mediaId, 0, localUri = localUri)),
                ),
                attemptCount = 2,
            )
        }
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt).apply {
            completeMessagesById[messageId] = remoteMessage(
                id = messageId,
                senderId = senderId,
                text = null,
                status = MessageSendStatus.SENT,
                media = listOf(remoteMedia(messageId, mediaId.toString(), 0)),
            )
        }
        val repository = createRepository(local, remote, RecordingTextMessageSendScheduler(events))
        val syncJob = launch { repository.startRealtimeSync() }
        yield()

        remote.emitRemoteMessageId(messageId)
        yield()

        repository.stopRealtimeSync()
        syncJob.join()

        assertTrue(local.upsertedMediaBatches.isEmpty())
        assertEquals(localUri, requireNotNull(local.currentMessage).media.single().localUri)
        assertEquals(MessageSendStatus.SENT, requireNotNull(local.currentMessage).sendStatus)
        assertEquals(2, local.sendAttemptCount)
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
    fun loadLatestMessages_persistsRemotePageAsSentWithUsersAndMedia() = runBlocking {
        val events = mutableListOf<String>()
        val sender = remoteUser(senderId)
        val otherSenderId = UUID.fromString("44eed91f-846c-49c8-851d-bca519b01432")
        val otherSender = remoteUser(otherSenderId)
        val messageId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val mediaMessageId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val media = listOf(
            remoteMedia(mediaMessageId, "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 0),
            remoteMedia(mediaMessageId, "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", 1),
        )
        val local = RecordingLocalDataSource(events)
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt).apply {
            usersById[sender.id] = sender
            usersById[otherSender.id] = otherSender
            latestMessages = listOf(
                remoteMessage(
                    id = messageId,
                    senderId = senderId,
                    text = "Latest text",
                    status = MessageSendStatus.SENDING,
                ),
                remoteMessage(
                    id = mediaMessageId,
                    senderId = otherSenderId,
                    text = null,
                    status = MessageSendStatus.FAILED,
                    media = media,
                ),
            )
        }
        val repository = createRepository(local, remote, RecordingTextMessageSendScheduler(events))

        repository.loadLatestMessages(20)

        assertEquals(20, remote.latestLimit)
        assertEquals(listOf(senderId, otherSenderId), remote.requestedUserIds)
        assertEquals(listOf(sender, otherSender), local.upsertedUsers)
        val persisted = local.upsertedMessagePages.single()
        assertEquals(listOf(messageId, mediaMessageId), persisted.map(Message::id))
        assertTrue(persisted.all { message -> message.sendStatus == MessageSendStatus.SENT })
        assertEquals(media, persisted[1].media)
        assertEquals(
            listOf("remote:latest", "remote:getUser", "remote:getUser", "local:upsertUsers", "local:upsertMessages"),
            events,
        )
    }

    @Test
    fun loadOlderMessages_usesOldestSentMessageAsCursor() = runBlocking {
        val events = mutableListOf<String>()
        val oldestSentId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1")
        val newestSentId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2")
        val olderMessageId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa0")
        val oldestSentCreatedAt = Instant.parse("2026-08-23T12:00:00Z")
        val sender = remoteUser(senderId)
        val local = RecordingLocalDataSource(events).apply {
            seedUser(sender)
            seedMessage(
                persistedTextMessage(
                    messageId = newestSentId,
                    status = MessageSendStatus.SENT,
                    createdAt = Instant.parse("2026-08-23T12:01:00Z"),
                ),
            )
            seedMessage(
                persistedTextMessage(
                    messageId = oldestSentId,
                    status = MessageSendStatus.SENT,
                    createdAt = oldestSentCreatedAt,
                ),
            )
        }
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt).apply {
            olderMessages = listOf(
                remoteMessage(
                    id = olderMessageId,
                    senderId = senderId,
                    text = "Older text",
                    status = MessageSendStatus.SENT,
                    createdAt = Instant.parse("2026-08-23T11:59:59Z"),
                ),
            )
        }
        val repository = createRepository(local, remote, RecordingTextMessageSendScheduler(events))

        val pageSize = repository.loadOlderMessages(limit = 10)

        assertEquals(oldestSentCreatedAt, remote.olderCursorCreatedAt)
        assertEquals(oldestSentId, remote.olderCursorMessageId)
        assertEquals(10, remote.olderLimit)
        assertEquals(1, pageSize)
        assertTrue(remote.requestedUserIds.isEmpty())
        assertEquals(listOf(olderMessageId), local.upsertedMessagePages.single().map(Message::id))
        assertEquals(
            listOf("remote:older", "local:upsertMessages"),
            events,
        )
    }

    @Test
    fun loadOlderMessages_persistsMediaWithPositionsWithoutChangingCursor() = runBlocking {
        val events = mutableListOf<String>()
        val oldestSentId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa31")
        val olderMessageId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa30")
        val oldestSentCreatedAt = Instant.parse("2026-08-23T12:00:00Z")
        val media = listOf(
            remoteMedia(olderMessageId, "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb30", 0),
            remoteMedia(olderMessageId, "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb31", 1),
        )
        val sender = remoteUser(senderId)
        val local = RecordingLocalDataSource(events).apply {
            seedUser(sender)
            seedMessage(
                persistedTextMessage(
                    messageId = oldestSentId,
                    status = MessageSendStatus.SENT,
                    createdAt = oldestSentCreatedAt,
                ),
            )
        }
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt).apply {
            olderMessages = listOf(
                remoteMessage(
                    id = olderMessageId,
                    senderId = senderId,
                    text = "Older caption",
                    status = MessageSendStatus.SENT,
                    createdAt = Instant.parse("2026-08-23T11:59:59Z"),
                    media = media,
                ),
            )
        }
        val repository = createRepository(local, remote, RecordingTextMessageSendScheduler(events))

        val pageSize = repository.loadOlderMessages(limit = 20)

        assertEquals(1, pageSize)
        assertEquals(oldestSentCreatedAt, remote.olderCursorCreatedAt)
        assertEquals(oldestSentId, remote.olderCursorMessageId)
        val persisted = local.upsertedMessagePages.single().single()
        assertEquals(olderMessageId, persisted.id)
        assertEquals("Older caption", persisted.textContent)
        assertEquals(media, persisted.media)
        assertEquals(listOf(0, 1), persisted.media.map(MessageMedia::position))
    }

    @Test
    fun loadOlderMessages_doesNotUseOlderLocalFailedMessageAsCursor() = runBlocking {
        val events = mutableListOf<String>()
        val loadedOldestSentId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb81")
        val failedLocalId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb50")
        val skippedRangeMessageId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb70")
        val loadedOldestSentAt = Instant.parse("2026-08-23T12:00:00Z")
        val sender = remoteUser(senderId)
        val local = RecordingLocalDataSource(events).apply {
            seedUser(sender)
            seedMessage(
                persistedTextMessage(
                    messageId = loadedOldestSentId,
                    status = MessageSendStatus.SENT,
                    createdAt = loadedOldestSentAt,
                ),
            )
            seedMessage(
                persistedTextMessage(
                    messageId = failedLocalId,
                    status = MessageSendStatus.FAILED,
                    createdAt = Instant.parse("2026-08-23T11:50:00Z"),
                ),
            )
        }
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt).apply {
            olderMessages = listOf(
                remoteMessage(
                    id = skippedRangeMessageId,
                    senderId = senderId,
                    text = "Between failed local and loaded page",
                    status = MessageSendStatus.SENT,
                    createdAt = Instant.parse("2026-08-23T11:40:00Z"),
                ),
            )
        }
        val repository = createRepository(local, remote, RecordingTextMessageSendScheduler(events))

        repository.loadOlderMessages(limit = 20)

        assertEquals(loadedOldestSentAt, remote.olderCursorCreatedAt)
        assertEquals(loadedOldestSentId, remote.olderCursorMessageId)
        assertEquals(
            listOf(skippedRangeMessageId),
            local.upsertedMessagePages.single().map(Message::id),
        )
    }

    @Test
    fun loadOlderMessages_emptyRemotePageReturnsZero() = runBlocking {
        val events = mutableListOf<String>()
        val sentId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
        val sentAt = Instant.parse("2026-08-23T12:00:00Z")
        val local = RecordingLocalDataSource(events).apply {
            seedMessage(
                persistedTextMessage(
                    messageId = sentId,
                    status = MessageSendStatus.SENT,
                    createdAt = sentAt,
                ),
            )
        }
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt).apply {
            olderMessages = emptyList()
        }
        val repository = createRepository(local, remote, RecordingTextMessageSendScheduler(events))

        val pageSize = repository.loadOlderMessages(limit = 20)

        assertEquals(0, pageSize)
        assertEquals(sentAt, remote.olderCursorCreatedAt)
        assertEquals(sentId, remote.olderCursorMessageId)
        assertTrue(local.upsertedMessagePages.isEmpty())
        assertEquals(listOf("remote:older"), events)
    }

    @Test
    fun loadLatestMessages_duplicateUuid_upsertsSameIdAgain() = runBlocking {
        val events = mutableListOf<String>()
        val messageId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val sender = remoteUser(senderId)
        val existing = remoteMessage(
            id = messageId,
            senderId = senderId,
            text = "First",
            status = MessageSendStatus.SENT,
        )
        val duplicate = existing.copy(textContent = "Updated remotely")
        val local = RecordingLocalDataSource(events).apply { seedUser(sender) }
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt)
        val repository = createRepository(local, remote, RecordingTextMessageSendScheduler(events))

        remote.latestMessages = listOf(existing)
        repository.loadLatestMessages()
        remote.latestMessages = listOf(duplicate)
        repository.loadLatestMessages()

        assertEquals(listOf(messageId), local.messagesById.keys.toList())
        assertEquals("Updated remotely", local.messagesById.getValue(messageId).textContent)
        assertEquals(2, local.upsertedMessagePages.size)
        assertEquals(messageId, local.upsertedMessagePages[0].single().id)
        assertEquals(messageId, local.upsertedMessagePages[1].single().id)
    }

    @Test
    fun loadLatestMessages_remoteFailure_keepsExistingLocalData() = runBlocking {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("network unavailable")
        val existing = persistedTextMessage(
            UUID.fromString("44444444-4444-4444-4444-444444444444"),
            MessageSendStatus.SENT,
        )
        val local = RecordingLocalDataSource(events).apply { seedMessage(existing) }
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt, failure)
        val repository = createRepository(local, remote, RecordingTextMessageSendScheduler(events))
        var thrown: Throwable? = null

        try {
            repository.loadLatestMessages()
        } catch (exception: Throwable) {
            thrown = exception
        }

        assertSame(failure, thrown)
        assertEquals(existing, local.currentMessage)
        assertTrue(local.upsertedMessagePages.isEmpty())
        assertTrue(local.upsertedUsers.isEmpty())
        assertEquals(0, local.upsertCount)
        assertEquals(listOf("remote:latest"), events)
    }

    @Test
    fun startRealtimeSync_persistsCompleteRemoteMessageAndSender() = runBlocking {
        val events = mutableListOf<String>()
        val messageId = UUID.fromString("55555555-5555-5555-5555-555555555555")
        val sender = remoteUser(senderId)
        val local = RecordingLocalDataSource(events)
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt).apply {
            usersById[sender.id] = sender
            completeMessagesById[messageId] = remoteMessage(
                id = messageId,
                senderId = senderId,
                text = "From another device",
                status = MessageSendStatus.SENDING,
            )
        }
        val repository = createRepository(local, remote, RecordingTextMessageSendScheduler(events))
        val syncJob = launch { repository.startRealtimeSync() }
        yield()

        remote.emitRemoteMessageId(messageId)
        yield()

        repository.stopRealtimeSync()
        syncJob.join()

        assertEquals(listOf(messageId), remote.emittedMessageLookups)
        assertEquals(listOf(sender), local.upsertedUsers)
        val persisted = local.upsertedMessagePages.single().single()
        assertEquals(messageId, persisted.id)
        assertEquals(MessageSendStatus.SENT, persisted.sendStatus)
        assertEquals(
            listOf("remote:getMessage", "remote:getUser", "local:upsertUsers", "local:upsertMessages"),
            events,
        )
    }

    @Test
    fun startRealtimeSync_existingOptimisticUuid_reconcilesSentWithoutResettingAttempts() =
        runBlocking {
            val events = mutableListOf<String>()
            val messageId = UUID.fromString("66666666-6666-6666-6666-666666666666")
            val sender = remoteUser(senderId)
            val local = RecordingLocalDataSource(events).apply {
                seedUser(sender)
                seedMessage(
                    persistedTextMessage(messageId, MessageSendStatus.SENDING),
                    attemptCount = 2,
                )
            }
            val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt).apply {
                completeMessagesById[messageId] = remoteMessage(
                    id = messageId,
                    senderId = senderId,
                    text = "Existing message",
                    status = MessageSendStatus.SENT,
                )
            }
            val repository = createRepository(local, remote, RecordingTextMessageSendScheduler(events))
            val syncJob = launch { repository.startRealtimeSync() }
            yield()

            remote.emitRemoteMessageId(messageId)
            yield()

            repository.stopRealtimeSync()
            syncJob.join()

            assertEquals(2, local.sendAttemptCount)
            assertEquals(MessageSendStatus.SENT, requireNotNull(local.currentMessage).sendStatus)
            assertEquals(serverCreatedAt, requireNotNull(local.currentMessage).createdAt)
            assertTrue(local.upsertedMessagePages.isEmpty())
            assertEquals(listOf("remote:getMessage", "local:SENT"), events)
        }

    @Test
    fun startRealtimeSync_secondStart_doesNotDuplicateSubscription() = runBlocking {
        val events = mutableListOf<String>()
        val messageId = UUID.fromString("77777777-7777-7777-7777-777777777777")
        val sender = remoteUser(senderId)
        val local = RecordingLocalDataSource(events).apply { seedUser(sender) }
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt).apply {
            completeMessagesById[messageId] = remoteMessage(
                id = messageId,
                senderId = senderId,
                text = "Once",
                status = MessageSendStatus.SENT,
            )
        }
        val repository = createRepository(local, remote, RecordingTextMessageSendScheduler(events))
        val first = launch { repository.startRealtimeSync() }
        val second = launch { repository.startRealtimeSync() }
        yield()

        remote.emitRemoteMessageId(messageId)
        yield()

        repository.stopRealtimeSync()
        first.join()
        second.join()

        assertEquals(1, remote.emittedMessageLookups.size)
        assertEquals(1, local.upsertedMessagePages.size)
    }

    @Test
    fun startRealtimeSync_getMessageFailure_keepsExistingRoomData() = runBlocking {
        val events = mutableListOf<String>()
        val existing = persistedTextMessage(
            UUID.fromString("88888888-8888-8888-8888-888888888888"),
            MessageSendStatus.SENT,
        )
        val local = RecordingLocalDataSource(events).apply { seedMessage(existing) }
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt).apply {
            getMessageFailure = IllegalStateException("realtime payload failed")
        }
        val repository = createRepository(local, remote, RecordingTextMessageSendScheduler(events))
        val syncJob = launch { repository.startRealtimeSync() }
        yield()

        remote.emitRemoteMessageId(existing.id)
        yield()

        repository.stopRealtimeSync()
        syncJob.join()

        assertEquals(existing, local.currentMessage)
        assertTrue(local.upsertedMessagePages.isEmpty())
        assertEquals(0, local.upsertCount)
        assertEquals(listOf("remote:getMessage"), events)
    }

    @Test
    fun startRealtimeSync_newRemoteTextOnly_persistsToRoom() = runBlocking {
        val events = mutableListOf<String>()
        val messageId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa21")
        val sender = remoteUser(senderId)
        val local = RecordingLocalDataSource(events)
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt).apply {
            usersById[sender.id] = sender
            completeMessagesById[messageId] = remoteMessage(
                id = messageId,
                senderId = senderId,
                text = "Live text",
                status = MessageSendStatus.SENT,
            )
        }
        val repository = createRepository(local, remote, RecordingTextMessageSendScheduler(events))
        val syncJob = launch { repository.startRealtimeSync() }
        yield()

        remote.emitRemoteMessageId(messageId)
        yield()

        repository.stopRealtimeSync()
        syncJob.join()

        val persisted = local.upsertedMessagePages.single().single()
        assertEquals(messageId, persisted.id)
        assertEquals("Live text", persisted.textContent)
        assertTrue(persisted.media.isEmpty())
        assertEquals(MessageSendStatus.SENT, persisted.sendStatus)
    }

    @Test
    fun startRealtimeSync_newRemoteMediaOnly_persistsMessageAndMedia() = runBlocking {
        val events = mutableListOf<String>()
        val messageId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa22")
        val mediaId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb22"
        val sender = remoteUser(senderId)
        val media = listOf(remoteMedia(messageId, mediaId, 0))
        val local = RecordingLocalDataSource(events)
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt).apply {
            usersById[sender.id] = sender
            completeMessagesById[messageId] = remoteMessage(
                id = messageId,
                senderId = senderId,
                text = null,
                status = MessageSendStatus.SENT,
                media = media,
            )
        }
        val repository = createRepository(local, remote, RecordingTextMessageSendScheduler(events))
        val syncJob = launch { repository.startRealtimeSync() }
        yield()

        remote.emitRemoteMessageId(messageId)
        yield()

        repository.stopRealtimeSync()
        syncJob.join()

        val persisted = local.upsertedMessagePages.single().single()
        assertEquals(messageId, persisted.id)
        assertEquals(null, persisted.textContent)
        assertEquals(media, persisted.media)
        assertEquals(MessageSendStatus.SENT, persisted.sendStatus)
    }

    @Test
    fun startRealtimeSync_newRemoteMediaAndText_persistsBothOnSameUuid() = runBlocking {
        val events = mutableListOf<String>()
        val messageId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa23")
        val media = listOf(
            remoteMedia(messageId, "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb23", 0),
            remoteMedia(messageId, "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb24", 1),
        )
        val sender = remoteUser(senderId)
        val local = RecordingLocalDataSource(events)
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt).apply {
            usersById[sender.id] = sender
            completeMessagesById[messageId] = remoteMessage(
                id = messageId,
                senderId = senderId,
                text = "Caption",
                status = MessageSendStatus.SENT,
                media = media,
            )
        }
        val repository = createRepository(local, remote, RecordingTextMessageSendScheduler(events))
        val syncJob = launch { repository.startRealtimeSync() }
        yield()

        remote.emitRemoteMessageId(messageId)
        yield()

        repository.stopRealtimeSync()
        syncJob.join()

        val persisted = local.upsertedMessagePages.single().single()
        assertEquals(messageId, persisted.id)
        assertEquals("Caption", persisted.textContent)
        assertEquals(media, persisted.media)
        assertEquals(1, local.messagesById.size)
    }

    @Test
    fun startRealtimeSync_duplicateEvent_doesNotInsertDuplicateMessage() = runBlocking {
        val events = mutableListOf<String>()
        val messageId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa25")
        val media = listOf(remoteMedia(messageId, "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb25", 0))
        val sender = remoteUser(senderId)
        val local = RecordingLocalDataSource(events).apply { seedUser(sender) }
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt).apply {
            completeMessagesById[messageId] = remoteMessage(
                id = messageId,
                senderId = senderId,
                text = "Caption",
                status = MessageSendStatus.SENT,
                media = media,
            )
        }
        val repository = createRepository(local, remote, RecordingTextMessageSendScheduler(events))
        val syncJob = launch { repository.startRealtimeSync() }
        yield()

        remote.emitRemoteMessageId(messageId)
        yield()
        remote.emitRemoteMessageId(messageId)
        yield()

        repository.stopRealtimeSync()
        syncJob.join()

        assertEquals(1, local.messagesById.size)
        assertEquals(1, local.upsertedMessagePages.size)
        assertTrue(local.upsertedMediaBatches.isEmpty())
        assertEquals(media, local.messagesById.getValue(messageId).media)
        assertEquals(2, remote.emittedMessageLookups.size)
    }

    @Test
    fun startRealtimeSync_getMessageEmptyThenMediaEvent_fillsMissingRemoteMedia() = runBlocking {
        val events = mutableListOf<String>()
        val messageId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa26")
        val media = listOf(remoteMedia(messageId, "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb26", 0))
        val sender = remoteUser(senderId)
        val incomplete = remoteMessage(
            id = messageId,
            senderId = senderId,
            text = "Caption",
            status = MessageSendStatus.SENT,
        )
        val complete = incomplete.copy(media = media)
        val local = RecordingLocalDataSource(events).apply { seedUser(sender) }
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt).apply {
            enqueueGetMessageResult(messageId, incomplete)
            enqueueGetMessageResult(messageId, complete)
            completeMessagesById[messageId] = complete
        }
        val repository = createRepository(local, remote, RecordingTextMessageSendScheduler(events))
        val syncJob = launch { repository.startRealtimeSync() }
        yield()

        remote.emitRemoteMessageId(messageId)
        yield()
        remote.emitRemoteMessageId(messageId)
        yield()

        repository.stopRealtimeSync()
        syncJob.join()

        assertEquals(1, local.messagesById.size)
        assertEquals(1, local.upsertedMessagePages.size)
        assertEquals(listOf(media), local.upsertedMediaBatches)
        assertEquals(media, local.messagesById.getValue(messageId).media)
        assertEquals("Caption", local.messagesById.getValue(messageId).textContent)
    }

    @Test
    fun startRealtimeSync_getMessageNullThenRetry_persistsWhenVisible() = runBlocking {
        val events = mutableListOf<String>()
        val messageId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa27")
        val media = listOf(remoteMedia(messageId, "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb27", 0))
        val sender = remoteUser(senderId)
        val complete = remoteMessage(
            id = messageId,
            senderId = senderId,
            text = "Caption",
            status = MessageSendStatus.SENT,
            media = media,
        )
        val local = RecordingLocalDataSource(events)
        val remote = RecordingRemoteDataSource(events, serverCreatedAt, serverUpdatedAt).apply {
            usersById[sender.id] = sender
            enqueueGetMessageResult(messageId, null)
            completeMessagesById[messageId] = complete
        }
        val repository = createRepository(local, remote, RecordingTextMessageSendScheduler(events))
        val syncJob = launch { repository.startRealtimeSync() }
        yield()

        remote.emitRemoteMessageId(messageId)
        yield()
        assertTrue(local.upsertedMessagePages.isEmpty())

        remote.emitRemoteMessageId(messageId)
        yield()

        repository.stopRealtimeSync()
        syncJob.join()

        val persisted = local.upsertedMessagePages.single().single()
        assertEquals(messageId, persisted.id)
        assertEquals(media, persisted.media)
        assertEquals("Caption", persisted.textContent)
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
        outgoingMediaStore: OutgoingMediaStore = RecordingOutgoingMediaStore(mutableListOf()),
        mediaMessageSendScheduler: MediaMessageSendScheduler =
            RecordingMediaMessageSendScheduler(mutableListOf()),
    ): DefaultChatRepository =
        DefaultChatRepository(
            localDataSource = localDataSource,
            remoteDataSource = remoteDataSource,
            userIdentityStore = object : UserIdentityStore {
                override suspend fun getOrCreateUserId(): UUID = senderId
            },
            textMessageSendScheduler = scheduler,
            mediaMessageSendScheduler = mediaMessageSendScheduler,
            outgoingMediaStore = outgoingMediaStore,
        )

    private fun pendingMedia(
        uri: String,
        mimeType: String = "image/jpeg",
        type: MediaType = MediaType.IMAGE,
        sizeBytes: Long? = 12L,
    ): PendingMedia =
        PendingMedia(
            localUri = uri,
            mediaType = type,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            width = 100,
            height = 80,
        )

    private fun persistedMediaMessage(
        messageId: UUID,
        media: List<MessageMedia>,
        status: MessageSendStatus = MessageSendStatus.SENDING,
    ): Message =
        Message(
            id = messageId,
            senderId = senderId,
            textContent = null,
            createdAt = Instant.parse("2026-08-23T12:00:00Z"),
            updatedAt = Instant.parse("2026-08-23T12:00:00Z"),
            media = media,
            sendStatus = status,
        )

    private fun localMedia(
        messageId: UUID,
        mediaId: UUID,
        position: Int,
        localUri: String = "file:///outgoing-media/$messageId/$mediaId.jpg",
        uploadStatus: MediaUploadStatus = MediaUploadStatus.PENDING,
        storagePath: String? = null,
    ): MessageMedia =
        MessageMedia(
            id = mediaId,
            messageId = messageId,
            storagePath = storagePath,
            mediaType = MediaType.IMAGE,
            mimeType = "image/jpeg",
            position = position,
            sizeBytes = 12L,
            width = 100,
            height = 80,
            localUri = localUri,
            uploadStatus = uploadStatus,
        )

    private fun persistedTextMessage(
        messageId: UUID,
        status: MessageSendStatus,
        createdAt: Instant = Instant.parse("2026-08-23T12:00:00Z"),
    ): Message =
        Message(
            id = messageId,
            senderId = senderId,
            textContent = "Existing message",
            createdAt = createdAt,
            updatedAt = createdAt,
            media = emptyList(),
            sendStatus = status,
        )

    private fun remoteUser(userId: UUID): User =
        User(
            id = userId,
            username = "user-$userId",
            profileImagePath = null,
            age = null,
            createdAt = Instant.parse("2026-08-23T11:00:00Z"),
            updatedAt = Instant.parse("2026-08-23T11:00:00Z"),
        )

    private fun remoteMessage(
        id: UUID,
        senderId: UUID,
        text: String?,
        status: MessageSendStatus,
        createdAt: Instant = serverCreatedAt,
        media: List<MessageMedia> = emptyList(),
    ): Message =
        Message(
            id = id,
            senderId = senderId,
            textContent = text,
            createdAt = createdAt,
            updatedAt = createdAt,
            media = media,
            sendStatus = status,
        )

    private fun remoteMedia(messageId: UUID, mediaId: String, position: Int): MessageMedia =
        MessageMedia(
            id = UUID.fromString(mediaId),
            messageId = messageId,
            storagePath = "$messageId/$mediaId.jpg",
            mediaType = MediaType.IMAGE,
            mimeType = "image/jpeg",
            position = position,
            sizeBytes = 12L,
            width = 100,
            height = 80,
            localUri = null,
            uploadStatus = MediaUploadStatus.UPLOADED,
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
    val messages = mutableListOf<ScheduledMessage>()
    val cancelled = mutableListOf<UUID>()

    override suspend fun enqueue(messageId: UUID, reason: TextMessageScheduleReason) {
        messages += ScheduledMessage(messageId, reason)
        events += "scheduler:enqueue"
        failure?.let { throw it }
    }

    override suspend fun cancel(messageId: UUID) {
        cancelled += messageId
        events += "scheduler:cancel"
    }
}

private data class ScheduledMessage(
    val messageId: UUID,
    val reason: TextMessageScheduleReason,
)

private data class ScheduledMediaMessage(
    val messageId: UUID,
    val reason: MediaMessageScheduleReason,
)

private class RecordingMediaMessageSendScheduler(
    private val events: MutableList<String>,
    private val failure: Exception? = null,
) : MediaMessageSendScheduler {
    val messages = mutableListOf<ScheduledMediaMessage>()
    val cancelled = mutableListOf<UUID>()

    override suspend fun enqueue(messageId: UUID, reason: MediaMessageScheduleReason) {
        messages += ScheduledMediaMessage(messageId, reason)
        events += "media-scheduler:enqueue"
        failure?.let { throw it }
    }

    override suspend fun cancel(messageId: UUID) {
        cancelled += messageId
        events += "media-scheduler:cancel"
    }
}

private data class CopiedOutgoingMedia(
    val sourceUri: String,
    val messageId: UUID,
    val mediaId: UUID,
    val durableUri: String,
)

private class RecordingOutgoingMediaStore(
    private val events: MutableList<String>,
    private val failOnCopyIndex: Int? = null,
) : OutgoingMediaStore {
    val copied = mutableListOf<CopiedOutgoingMedia>()
    val deletedMessageIds = mutableListOf<UUID>()
    val unreadableUris = mutableSetOf<String>()
    val copySizeByUri = mutableMapOf<String, Long>()
    var defaultCopySizeBytes: Long = 12L

    override fun copyIncoming(
        sourceUri: String,
        messageId: UUID,
        mediaId: UUID,
        mimeType: String,
    ): String {
        if (failOnCopyIndex != null && copied.size == failOnCopyIndex) {
            events += "store:copy-fail"
            throw IllegalStateException("copy failed")
        }
        val durableUri = "file:///outgoing-media/$messageId/$mediaId"
        copied += CopiedOutgoingMedia(
            sourceUri = sourceUri,
            messageId = messageId,
            mediaId = mediaId,
            durableUri = durableUri,
        )
        events += "store:copy"
        return durableUri
    }

    override fun deleteCopiedMedia(messageId: UUID) {
        copied.removeAll { item -> item.messageId == messageId }
        deletedMessageIds += messageId
        events += "store:delete"
    }

    override fun hasReadableCopy(localUri: String): Boolean =
        localUri.isNotBlank() && localUri !in unreadableUris

    override fun copySizeBytes(localUri: String): Long =
        copySizeByUri[localUri] ?: defaultCopySizeBytes

    override fun readCopyBytes(localUri: String): ByteArray {
        if (localUri in unreadableUris) {
            error("Outgoing media copy is missing or unreadable.")
        }
        return byteArrayOf(1, 2, 3)
    }
}

private class RecordingLocalDataSource(
    private val events: MutableList<String>,
    private val upsertFailure: Exception? = null,
) : ChatLocalDataSource {
    var persistedMessage: Message? = null
    var currentMessage: Message? = null
    var upsertCount = 0
    var sendAttemptCount = 0
    val stateUpdates = mutableListOf<SendStateUpdate>()
    val upsertedMessagePages = mutableListOf<List<Message>>()
    val upsertedUsers = mutableListOf<User>()
    val messagesById = mutableMapOf<UUID, Message>()
    val deletedMessageIds = mutableListOf<UUID>()
    val upsertedMediaBatches = mutableListOf<List<MessageMedia>>()
    val mediaUploadErrors = mutableMapOf<UUID, String?>()
    private val usersById = mutableMapOf<UUID, User>()

    override suspend fun upsertMessage(message: Message) {
        upsertCount += 1
        persistedMessage = message
        currentMessage = message
        sendAttemptCount = 0
        events += "local:${message.sendStatus}"
        upsertFailure?.let { throw it }
    }

    override suspend fun deleteMessage(messageId: UUID) {
        deletedMessageIds += messageId
        if (persistedMessage?.id == messageId) {
            persistedMessage = null
        }
        if (currentMessage?.id == messageId) {
            currentMessage = null
        }
        messagesById.remove(messageId)
        events += "local:delete"
    }

    fun seedMessage(message: Message, attemptCount: Int = 1) {
        currentMessage = message
        messagesById[message.id] = message
        sendAttemptCount = attemptCount
    }

    fun seedUser(user: User) {
        usersById[user.id] = user
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
        val existing = currentMessage?.takeIf { message -> message.id == messageId }
            ?: messagesById.getValue(messageId)
        val updated = existing.copy(
            createdAt = createdAt,
            updatedAt = updatedAt,
            sendStatus = MessageSendStatus.SENT,
        )
        if (currentMessage?.id == messageId) {
            currentMessage = updated
        }
        messagesById[messageId] = updated
        events += "local:SENT"
    }

    override suspend fun getMessageById(messageId: UUID): Message? =
        currentMessage?.takeIf { it.id == messageId } ?: messagesById[messageId]

    override fun observeMessages(): Flow<List<Message>> = emptyFlow()
    override suspend fun upsertUser(user: User) = unused()
    override suspend fun upsertUsers(users: List<User>) {
        users.forEach { user -> usersById[user.id] = user }
        upsertedUsers += users
        events += "local:upsertUsers"
    }
    override suspend fun getUserById(userId: UUID): User? = usersById[userId]
    override fun observeUserById(userId: UUID): Flow<User?> = unused()
    override fun observeUsers(): Flow<List<User>> = unused()
    override suspend fun upsertMessages(messages: List<Message>) {
        upsertedMessagePages += messages
        messages.forEach { message ->
            messagesById[message.id] = message
            if (currentMessage == null || currentMessage?.id == message.id) {
                currentMessage = message
            }
        }
        events += "local:upsertMessages"
    }
    override suspend fun getLatestMessages(limit: Int): List<Message> = unused()
    override suspend fun getOlderMessages(
        cursorCreatedAt: Instant,
        cursorMessageId: UUID,
        limit: Int,
    ): List<Message> = unused()
    override suspend fun getMessagesByStatuses(statuses: List<MessageSendStatus>): List<Message> =
        unused()

    override suspend fun getOldestMessageBySendStatus(status: MessageSendStatus): Message? =
        messagesById.values
            .filter { message -> message.sendStatus == status }
            .minWithOrNull(compareBy<Message> { message -> message.createdAt }.thenBy { message -> message.id })
    override suspend fun upsertMedia(media: MessageMedia) {
        upsertMedia(listOf(media))
    }

    override suspend fun upsertMedia(items: List<MessageMedia>) {
        upsertedMediaBatches += items
        items.groupBy(MessageMedia::messageId).forEach { (messageId, mediaItems) ->
            val existing = currentMessage?.takeIf { message -> message.id == messageId }
                ?: messagesById[messageId]
                ?: return@forEach
            val byId = existing.media.associateBy(MessageMedia::id).toMutableMap()
            mediaItems.forEach { media -> byId[media.id] = media }
            val merged = existing.copy(
                media = byId.values.sortedBy(MessageMedia::position),
            )
            messagesById[messageId] = merged
            if (currentMessage?.id == messageId) {
                currentMessage = merged
            }
        }
        events += "local:upsertMedia"
    }

    override suspend fun getMediaForMessage(messageId: UUID): List<MessageMedia> = unused()
    override suspend fun getMediaForMessages(messageIds: List<UUID>): List<MessageMedia> = unused()
    override fun observeMediaForMessage(messageId: UUID): Flow<List<MessageMedia>> = unused()
    override suspend fun beginMediaUploadAttempt(mediaId: UUID) {
        updateMedia(mediaId) { media -> media.copy(uploadStatus = MediaUploadStatus.UPLOADING) }
        events += "local:media-UPLOADING"
    }
    override suspend fun updateMediaUploadProgress(
        mediaId: UUID,
        status: MediaUploadStatus,
        progress: Int,
    ) {
        updateMedia(mediaId) { media -> media.copy(uploadStatus = status) }
        events += "local:media-progress"
    }
    override suspend fun markMediaUploaded(mediaId: UUID, storagePath: String) {
        updateMedia(mediaId) { media ->
            media.copy(
                storagePath = storagePath,
                uploadStatus = MediaUploadStatus.UPLOADED,
            )
        }
        events += "local:media-UPLOADED"
    }
    override suspend fun markMediaUploadFailed(
        mediaId: UUID,
        error: String?,
    ) {
        mediaUploadErrors[mediaId] = error
        updateMedia(mediaId) { media -> media.copy(uploadStatus = MediaUploadStatus.FAILED) }
        events += "local:media-FAILED"
    }
    override suspend fun getMediaByStatuses(statuses: List<MediaUploadStatus>): List<MessageMedia> =
        unused()
    override suspend fun deleteMediaForMessage(messageId: UUID) = unused()

    private fun updateMedia(mediaId: UUID, transform: (MessageMedia) -> MessageMedia) {
        val message = currentMessage ?: return
        currentMessage = message.copy(
            media = message.media.map { media ->
                if (media.id == mediaId) transform(media) else media
            },
        )
    }
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
    var latestMessages: List<Message> = emptyList()
    var olderMessages: List<Message> = emptyList()
    var latestLimit: Int? = null
    var olderCursorCreatedAt: Instant? = null
    var olderCursorMessageId: UUID? = null
    var olderLimit: Int? = null
    val usersById = mutableMapOf<UUID, User>()
    val requestedUserIds = mutableListOf<UUID>()
    val completeMessagesById = mutableMapOf<UUID, Message>()
    val emittedMessageLookups = mutableListOf<UUID>()
    var getMessageFailure: Exception? = null
    private val pendingGetMessageResults = mutableMapOf<UUID, ArrayDeque<Message?>>()
    val uploadedMedia = mutableListOf<UploadedChatMedia>()
    val createdMediaMessages = mutableListOf<CreatedMediaMessage>()
    var uploadFailure: Exception? = null
    var uploadFailureMediaId: UUID? = null
    var createMediaFailure: Exception? = null
    var cancelOnUploadMediaId: UUID? = null
    private val remoteMessageIds = MutableSharedFlow<UUID>(extraBufferCapacity = 16)

    suspend fun emitRemoteMessageId(messageId: UUID) {
        remoteMessageIds.emit(messageId)
    }

    fun enqueueGetMessageResult(messageId: UUID, result: Message?) {
        pendingGetMessageResults.getOrPut(messageId) { ArrayDeque() }.addLast(result)
    }

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
    override suspend fun getUser(userId: UUID): User? {
        events += "remote:getUser"
        requestedUserIds += userId
        return usersById[userId]
    }
    override suspend fun getMessage(messageId: UUID): Message? {
        events += "remote:getMessage"
        emittedMessageLookups += messageId
        getMessageFailure?.let { throw it }
        val queued = pendingGetMessageResults[messageId]
        if (queued != null && queued.isNotEmpty()) {
            return queued.removeFirst()
        }
        return completeMessagesById[messageId]
    }
    override fun observeRemoteMessageIds(): Flow<UUID> = remoteMessageIds
    override suspend fun getLatestMessages(limit: Int): List<Message> {
        events += "remote:latest"
        latestLimit = limit
        failure?.let { throw it }
        return latestMessages
    }
    override suspend fun getOlderMessages(
        cursorCreatedAt: Instant,
        cursorMessageId: UUID,
        limit: Int,
    ): List<Message> {
        events += "remote:older"
        olderCursorCreatedAt = cursorCreatedAt
        olderCursorMessageId = cursorMessageId
        olderLimit = limit
        failure?.let { throw it }
        return olderMessages
    }
    override suspend fun createMediaMessage(
        messageId: UUID,
        senderId: UUID,
        text: String?,
        media: List<MessageMedia>,
    ): Message {
        events += "remote:createMedia"
        createdMediaMessages += CreatedMediaMessage(
            messageId = messageId,
            senderId = senderId,
            text = text,
            media = media,
        )
        createMediaFailure?.let { throw it }
        return Message(
            id = messageId,
            senderId = senderId,
            textContent = text,
            createdAt = createdAt,
            updatedAt = updatedAt,
            media = media,
            sendStatus = MessageSendStatus.SENT,
        )
    }

    override suspend fun uploadChatMedia(
        messageId: UUID,
        mediaId: UUID,
        extension: String,
        bytes: ByteArray,
        mimeType: String,
    ): String {
        events += "remote:upload"
        val storagePath = "$messageId/$mediaId.$extension"
        uploadedMedia += UploadedChatMedia(
            messageId = messageId,
            mediaId = mediaId,
            extension = extension,
            bytes = bytes,
            mimeType = mimeType,
            storagePath = storagePath,
        )
        if (mediaId == cancelOnUploadMediaId) {
            throw CancellationException("upload cancelled")
        }
        if (uploadFailureMediaId == mediaId || (uploadFailureMediaId == null && uploadFailure != null)) {
            uploadFailure?.let { throw it }
        }
        return storagePath
    }
    override suspend fun uploadProfileImage(
        userId: UUID,
        bytes: ByteArray,
        mimeType: String,
        fileExtension: String,
    ): String = unused()
    override suspend fun deleteChatMediaObject(storagePath: String) = unused()
}

private data class UploadedChatMedia(
    val messageId: UUID,
    val mediaId: UUID,
    val extension: String,
    val bytes: ByteArray,
    val mimeType: String,
    val storagePath: String,
)

private data class CreatedMediaMessage(
    val messageId: UUID,
    val senderId: UUID,
    val text: String?,
    val media: List<MessageMedia>,
)

private fun unused(): Nothing = error("Not used by this test.")
