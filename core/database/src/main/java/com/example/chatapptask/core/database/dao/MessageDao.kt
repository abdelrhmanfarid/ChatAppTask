package com.example.chatapptask.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.chatapptask.core.database.entity.MessageEntity
import com.example.chatapptask.core.domain.model.MessageSendStatus
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Upsert
    suspend fun upsertMessage(message: MessageEntity)

    @Upsert
    suspend fun upsertMessages(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: UUID): MessageEntity?

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: UUID)

    @Query("SELECT * FROM messages ORDER BY created_at DESC, id DESC")
    fun observeMessages(): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT * FROM messages
        ORDER BY created_at DESC, id DESC
        LIMIT :limit
        """,
    )
    suspend fun getLatestMessages(limit: Int): List<MessageEntity>

    @Query(
        """
        SELECT * FROM messages
        WHERE created_at < :cursorCreatedAt
           OR (created_at = :cursorCreatedAt AND id < :cursorMessageId)
        ORDER BY created_at DESC, id DESC
        LIMIT :limit
        """,
    )
    suspend fun getOlderMessages(
        cursorCreatedAt: Instant,
        cursorMessageId: UUID,
        limit: Int,
    ): List<MessageEntity>

    @Query(
        """
        UPDATE messages
        SET send_status = :status,
            send_attempt_count = send_attempt_count + 1,
            last_send_error = NULL
        WHERE id = :messageId
        """,
    )
    suspend fun beginSendAttempt(
        messageId: UUID,
        status: MessageSendStatus,
    )

    @Query(
        """
        UPDATE messages
        SET send_status = :status,
            last_send_error = :lastError
        WHERE id = :messageId
        """,
    )
    suspend fun markSendFailed(
        messageId: UUID,
        status: MessageSendStatus,
        lastError: String?,
    )

    @Query(
        """
        UPDATE messages
        SET created_at = :createdAt,
            updated_at = :updatedAt,
            send_status = :status,
            last_send_error = NULL
        WHERE id = :messageId
        """,
    )
    suspend fun updateAfterSendSuccess(
        messageId: UUID,
        createdAt: Instant,
        updatedAt: Instant,
        status: MessageSendStatus,
    )

    @Query(
        """
        SELECT * FROM messages
        WHERE send_status IN (:statuses)
        ORDER BY created_at ASC, id ASC
        """,
    )
    suspend fun getMessagesBySendStatuses(
        statuses: List<MessageSendStatus>,
    ): List<MessageEntity>

    @Query(
        """
        SELECT * FROM messages
        WHERE send_status = :status
        ORDER BY created_at ASC, id ASC
        LIMIT 1
        """,
    )
    suspend fun getOldestMessageBySendStatus(status: MessageSendStatus): MessageEntity?
}
