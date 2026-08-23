package com.example.chatapptask.data.chat.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.chatapptask.data.chat.repository.DefaultChatRepository
import com.example.chatapptask.data.chat.repository.PersistedTextMessageException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.UUID

@HiltWorker
class SendTextMessageWorker @AssistedInject constructor(
    @Assisted applicationContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val chatRepository: DefaultChatRepository,
) : CoroutineWorker(applicationContext, workerParameters) {
    override suspend fun doWork(): Result {
        val messageId = inputData.getString(INPUT_MESSAGE_ID)?.toUuidOrNull()
            ?: return Result.failure()

        return try {
            chatRepository.sendPersistedTextMessage(messageId)
            Result.success()
        } catch (_: PersistedTextMessageException) {
            Result.failure()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val INPUT_MESSAGE_ID = "message_id"

        fun inputData(messageId: UUID): Data =
            workDataOf(INPUT_MESSAGE_ID to messageId.toString())
    }
}

private fun String.toUuidOrNull(): UUID? =
    try {
        UUID.fromString(this).takeIf { parsedId ->
            parsedId.toString().equals(this, ignoreCase = true)
        }
    } catch (_: IllegalArgumentException) {
        null
    }
