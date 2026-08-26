package com.example.chatapptask.data.chat.worker

import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkRequest
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MediaMessageSendSchedulerTest {
    private val messageId = UUID.fromString("4c5825d3-43d8-414a-808a-36fdf0f5d86f")

    @Test
    fun sameMessageId_mapsToStableUniqueWorkName() {
        assertEquals(
            mediaMessageUniqueWorkName(messageId),
            mediaMessageUniqueWorkName(messageId),
        )
        assertEquals(
            "send-media-message:$messageId",
            mediaMessageUniqueWorkName(messageId),
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
            MediaMessageScheduleReason.INITIAL.existingWorkPolicy,
        )
    }

    @Test
    fun manualRetry_replacesStaleOrCompletedUniqueWork() {
        assertEquals(
            ExistingWorkPolicy.REPLACE,
            MediaMessageScheduleReason.MANUAL_RETRY.existingWorkPolicy,
        )
    }

    @Test
    fun request_requiresConnectedNetworkUsesExponentialBackoffAndOnlyMessageId() {
        val workSpec = mediaMessageWorkRequest(messageId).workSpec

        assertEquals(NetworkType.CONNECTED, workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, workSpec.backoffPolicy)
        assertEquals(WorkRequest.MIN_BACKOFF_MILLIS, workSpec.backoffDelayDuration)
        assertEquals(
            messageId.toString(),
            workSpec.input.getString(SendMediaMessageWorker.INPUT_MESSAGE_ID),
        )
        assertEquals(null, workSpec.input.getString("local_uri"))
        assertEquals(null, workSpec.input.getString("uri"))
        assertFalse(workSpec.input.keyValueMap.keys.any { key -> key.contains("uri", ignoreCase = true) })
    }
}
