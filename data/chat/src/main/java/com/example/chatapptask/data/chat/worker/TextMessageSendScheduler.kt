package com.example.chatapptask.data.chat.worker

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject

interface TextMessageSendScheduler {
    suspend fun enqueue(messageId: UUID)
}

class WorkManagerTextMessageSendScheduler @Inject constructor(
    @ApplicationContext context: Context,
) : TextMessageSendScheduler {
    private val workManager = WorkManager.getInstance(context)

    override suspend fun enqueue(messageId: UUID) {
        val request = OneTimeWorkRequestBuilder<SendTextMessageWorker>()
            .setInputData(SendTextMessageWorker.inputData(messageId))
            .build()

        workManager.enqueue(request).await()
    }
}
