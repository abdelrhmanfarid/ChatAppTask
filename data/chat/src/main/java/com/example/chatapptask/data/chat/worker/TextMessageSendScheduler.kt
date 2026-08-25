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

interface TextMessageSendScheduler {
    suspend fun enqueue(messageId: UUID, reason: TextMessageScheduleReason)

    suspend fun cancel(messageId: UUID)
}

enum class TextMessageScheduleReason(
    internal val existingWorkPolicy: ExistingWorkPolicy,
) {
    INITIAL(ExistingWorkPolicy.KEEP),
    MANUAL_RETRY(ExistingWorkPolicy.REPLACE),
}

class WorkManagerTextMessageSendScheduler @Inject constructor(
    @ApplicationContext context: Context,
) : TextMessageSendScheduler {
    private val workManager = WorkManager.getInstance(context)

    override suspend fun enqueue(messageId: UUID, reason: TextMessageScheduleReason) {
        workManager.enqueueUniqueWork(
            textMessageUniqueWorkName(messageId),
            reason.existingWorkPolicy,
            textMessageWorkRequest(messageId),
        ).await()
    }

    override suspend fun cancel(messageId: UUID) {
        workManager.cancelUniqueWork(textMessageUniqueWorkName(messageId)).await()
    }
}

internal fun textMessageUniqueWorkName(messageId: UUID): String =
    "send-text-message:$messageId"

internal fun textMessageWorkRequest(messageId: UUID): OneTimeWorkRequest =
    OneTimeWorkRequestBuilder<SendTextMessageWorker>()
        .setInputData(SendTextMessageWorker.inputData(messageId))
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
