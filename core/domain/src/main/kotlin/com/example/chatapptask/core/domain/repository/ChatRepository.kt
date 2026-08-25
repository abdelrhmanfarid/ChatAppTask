package com.example.chatapptask.core.domain.repository

import com.example.chatapptask.core.domain.model.Message
import com.example.chatapptask.core.domain.model.PendingMedia
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeMessages(): Flow<List<Message>>

    suspend fun loadLatestMessages(limit: Int = 20)

    suspend fun loadOlderMessages(
        oldestCreatedAt: Instant,
        oldestMessageId: UUID,
        limit: Int = 20,
    )

    suspend fun sendTextMessage(text: String)

    suspend fun sendMediaMessage(
        media: List<PendingMedia>,
        text: String? = null,
    )

    suspend fun retryMessage(messageId: UUID)

    /**
     * Cancels outstanding unique send work for [messageId].
     *
     * This does not undo a remote insert that already succeeded. A local row
     * still marked sending is marked failed so it can be retried with the same
     * UUID. Realtime or a later fetch may still reconcile it to sent.
     */
    suspend fun cancelOutgoingSend(messageId: UUID)

    suspend fun retryMediaItem(
        messageId: UUID,
        mediaId: UUID,
    )

    suspend fun startRealtimeSync()

    suspend fun stopRealtimeSync()
}
