package com.example.chatapptask.data.chat.worker

import androidx.work.ListenableWorker
import com.example.chatapptask.data.chat.repository.PersistedTextMessageNotFoundException
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SendTextMessageWorkerTest {
    private val messageId = UUID.fromString("4c5825d3-43d8-414a-808a-36fdf0f5d86f")

    @Test
    fun success_mapsToWorkSuccess() {
        assertEquals(
            ListenableWorker.Result.success(),
            sendTextMessageWorkerResult(error = null),
        )
    }

    @Test
    fun persistedTextFailure_mapsToWorkFailure() {
        assertEquals(
            ListenableWorker.Result.failure(),
            sendTextMessageWorkerResult(PersistedTextMessageNotFoundException(messageId)),
        )
    }

    @Test
    fun retryableFailure_mapsToWorkRetry() {
        assertEquals(
            ListenableWorker.Result.retry(),
            sendTextMessageWorkerResult(IllegalStateException("network")),
        )
    }

    @Test
    fun cancellation_isRethrownInsteadOfRetry() {
        assertThrows(CancellationException::class.java) {
            sendTextMessageWorkerResult(CancellationException())
        }
    }

    @Test
    fun notificationActions_targetTheSameMessageUuid() {
        assertEquals(messageId, MessageSendWorkActions.messageIdFrom(messageId.toString()))
        assertEquals(
            textMessageUniqueWorkName(messageId),
            "send-text-message:$messageId",
        )
    }

    @Test
    fun notificationActions_rejectInvalidMessageIds() {
        assertNull(MessageSendWorkActions.messageIdFrom("not-a-uuid"))
        assertNull(MessageSendWorkActions.messageIdFrom(null as String?))
    }
}
