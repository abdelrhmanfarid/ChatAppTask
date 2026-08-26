package com.example.chatapptask.data.chat.worker

import androidx.work.ListenableWorker
import com.example.chatapptask.data.chat.repository.PermanentMediaUploadException
import com.example.chatapptask.data.chat.repository.PersistedMediaLocalFileMissingException
import com.example.chatapptask.data.chat.repository.PersistedMediaMessageNotFoundException
import com.example.chatapptask.data.chat.repository.PersistedMessageIsNotMediaException
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SendMediaMessageWorkerTest {
    private val messageId = UUID.fromString("4c5825d3-43d8-414a-808a-36fdf0f5d86f")
    private val mediaId = UUID.fromString("5d6936e4-54e9-525b-919b-47eef1f6e97a")

    @Test
    fun success_mapsToWorkSuccess() {
        assertEquals(
            ListenableWorker.Result.success(),
            sendMediaMessageWorkerResult(error = null),
        )
    }

    @Test
    fun persistedMediaFailure_mapsToWorkFailure() {
        assertEquals(
            ListenableWorker.Result.failure(),
            sendMediaMessageWorkerResult(PersistedMediaMessageNotFoundException(messageId)),
        )
        assertEquals(
            ListenableWorker.Result.failure(),
            sendMediaMessageWorkerResult(PersistedMessageIsNotMediaException(messageId)),
        )
        assertEquals(
            ListenableWorker.Result.failure(),
            sendMediaMessageWorkerResult(PersistedMediaLocalFileMissingException(messageId, mediaId)),
        )
        assertEquals(
            ListenableWorker.Result.failure(),
            sendMediaMessageWorkerResult(
                PermanentMediaUploadException("Each photo or video must be 50 MB or smaller."),
            ),
        )
    }

    @Test
    fun retryableFailure_mapsToWorkRetry() {
        assertEquals(
            ListenableWorker.Result.retry(),
            sendMediaMessageWorkerResult(IllegalStateException("work manager")),
        )
    }

    @Test
    fun cancellation_isRethrownInsteadOfRetry() {
        assertThrows(CancellationException::class.java) {
            sendMediaMessageWorkerResult(CancellationException())
        }
    }

    @Test
    fun notificationWorkKey_isDistinctFromTextAndUsesMessageUuid() {
        assertEquals(messageId, MessageSendWorkActions.messageIdFrom(messageId.toString()))
        assertEquals(
            "send-media-message:$messageId",
            mediaMessageUniqueWorkName(messageId),
        )
        assertEquals(
            "send-text-message:$messageId",
            textMessageUniqueWorkName(messageId),
        )
    }
}
