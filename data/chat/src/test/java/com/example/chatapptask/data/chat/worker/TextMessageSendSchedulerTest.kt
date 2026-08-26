package com.example.chatapptask.data.chat.worker

import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkRequest
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TextMessageSendSchedulerTest {
    private val messageId = UUID.fromString("4c5825d3-43d8-414a-808a-36fdf0f5d86f")

    @Test
    fun sameMessageId_mapsToStableUniqueWorkName() {
        assertEquals(
            textMessageUniqueWorkName(messageId),
            textMessageUniqueWorkName(messageId),
        )
        assertEquals(
            "send-text-message:$messageId",
            textMessageUniqueWorkName(messageId),
        )
    }

    @Test
    fun initialScheduling_keepsExistingUniqueWorkToPreventDuplicates() {
        assertEquals(
            ExistingWorkPolicy.KEEP,
            TextMessageScheduleReason.INITIAL.existingWorkPolicy,
        )
    }

    @Test
    fun manualRetry_replacesStaleOrCompletedUniqueWork() {
        assertEquals(
            ExistingWorkPolicy.REPLACE,
            TextMessageScheduleReason.MANUAL_RETRY.existingWorkPolicy,
        )
    }

    @Test
    fun request_requiresConnectedNetworkAndUsesExponentialMinimumBackoff() {
        val workSpec = textMessageWorkRequest(messageId).workSpec

        assertEquals(NetworkType.CONNECTED, workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, workSpec.backoffPolicy)
        assertEquals(WorkRequest.MIN_BACKOFF_MILLIS, workSpec.backoffDelayDuration)
        assertEquals(
            messageId.toString(),
            workSpec.input.getString(SendTextMessageWorker.INPUT_MESSAGE_ID),
        )
    }

    @Test
    fun notificationId_isDeterministicForTheSameWorkKey() {
        val workKey = textMessageUniqueWorkName(messageId)

        assertEquals(
            MessageSendWorkNotifications.notificationId(workKey),
            MessageSendWorkNotifications.notificationId(workKey),
        )
        assertEquals(workKey.hashCode(), MessageSendWorkNotifications.notificationId(workKey))
    }

    @Test
    fun actionRequestCodes_areStableAndDistinctForRetryAndCancel() {
        val workKey = textMessageUniqueWorkName(messageId)

        val retryCode = MessageSendWorkNotifications.actionRequestCode(
            workKey,
            MessageSendWorkActions.ACTION_RETRY,
        )
        val cancelCode = MessageSendWorkNotifications.actionRequestCode(
            workKey,
            MessageSendWorkActions.ACTION_CANCEL,
        )

        assertEquals(
            retryCode,
            MessageSendWorkNotifications.actionRequestCode(
                workKey,
                MessageSendWorkActions.ACTION_RETRY,
            ),
        )
        assertEquals(
            cancelCode,
            MessageSendWorkNotifications.actionRequestCode(
                workKey,
                MessageSendWorkActions.ACTION_CANCEL,
            ),
        )
        assertNotEquals(retryCode, cancelCode)
    }
}
