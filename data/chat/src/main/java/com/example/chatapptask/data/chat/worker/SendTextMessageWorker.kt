package com.example.chatapptask.data.chat.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.chatapptask.data.chat.repository.DefaultChatRepository
import com.example.chatapptask.data.chat.repository.PersistedTextMessageException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.UUID
import java.util.concurrent.CancellationException

@HiltWorker
class SendTextMessageWorker @AssistedInject constructor(
    @Assisted applicationContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val chatRepository: DefaultChatRepository,
) : CoroutineWorker(applicationContext, workerParameters) {
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val messageId = inputData.getString(INPUT_MESSAGE_ID)?.toUuidOrNull()
            ?: return MessageSendWorkNotifications.textMessageForegroundInfo(
                applicationContext,
                FALLBACK_NOTIFICATION_MESSAGE_ID,
            )
        return MessageSendWorkNotifications.textMessageForegroundInfo(applicationContext, messageId)
    }

    override suspend fun doWork(): Result {
        val messageId = inputData.getString(INPUT_MESSAGE_ID)?.toUuidOrNull()
            ?: return Result.failure()

        try {
            setForeground(
                MessageSendWorkNotifications.textMessageForegroundInfo(applicationContext, messageId),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Sending continues if the foreground notification cannot be shown.
        }

        return try {
            chatRepository.sendPersistedTextMessage(messageId)
            sendTextMessageWorkerResult(error = null)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            sendTextMessageWorkerResult(error)
        }
    }

    companion object {
        const val INPUT_MESSAGE_ID = "message_id"

        fun inputData(messageId: UUID): Data =
            workDataOf(INPUT_MESSAGE_ID to messageId.toString())
    }
}

internal fun sendTextMessageWorkerResult(error: Throwable?): ListenableWorker.Result {
    if (error is CancellationException) throw error
    return when (error) {
        null -> ListenableWorker.Result.success()
        is PersistedTextMessageException -> ListenableWorker.Result.failure()
        else -> ListenableWorker.Result.retry()
    }
}

private val FALLBACK_NOTIFICATION_MESSAGE_ID = UUID(0L, 0L)
