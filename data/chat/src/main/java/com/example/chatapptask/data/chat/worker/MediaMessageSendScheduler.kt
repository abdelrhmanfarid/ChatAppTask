package com.example.chatapptask.data.chat.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkRequest
import androidx.work.WorkManager
import androidx.work.await
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface MediaMessageSendScheduler {
    suspend fun enqueue(messageId: UUID, reason: MediaMessageScheduleReason)

    suspend fun cancel(messageId: UUID)
}

enum class MediaMessageScheduleReason(
    internal val existingWorkPolicy: ExistingWorkPolicy,
) {
    INITIAL(ExistingWorkPolicy.KEEP),
    MANUAL_RETRY(ExistingWorkPolicy.REPLACE),
}

class WorkManagerMediaMessageSendScheduler @Inject constructor(
    @ApplicationContext context: Context,
) : MediaMessageSendScheduler {
    private val workManager = WorkManager.getInstance(context)

    override suspend fun enqueue(messageId: UUID, reason: MediaMessageScheduleReason) {
        workManager.enqueueUniqueWork(
            mediaMessageUniqueWorkName(messageId),
            reason.existingWorkPolicy,
            mediaMessageWorkRequest(messageId),
        ).await()
    }

    override suspend fun cancel(messageId: UUID) {
        workManager.cancelUniqueWork(mediaMessageUniqueWorkName(messageId)).await()
    }
}

internal fun mediaMessageUniqueWorkName(messageId: UUID): String =
    "send-media-message:$messageId"

internal fun mediaMessageWorkRequest(messageId: UUID): OneTimeWorkRequest =
    OneTimeWorkRequestBuilder<SendMediaMessageWorker>()
        .setInputData(SendMediaMessageWorker.inputData(messageId))
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build(),
        )
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            WorkRequest.MIN_BACKOFF_MILLIS,
            TimeUnit.MILLISECONDS,
        )
        .build()
