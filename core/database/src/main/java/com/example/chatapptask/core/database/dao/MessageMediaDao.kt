package com.example.chatapptask.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.chatapptask.core.database.entity.MessageMediaEntity
import com.example.chatapptask.core.domain.model.MediaUploadStatus
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageMediaDao {
    @Upsert
    suspend fun upsertMedia(media: MessageMediaEntity)

    @Upsert
    suspend fun upsertMedia(items: List<MessageMediaEntity>)

    @Query("SELECT * FROM message_media WHERE message_id = :messageId ORDER BY position ASC")
    suspend fun getMediaForMessage(messageId: UUID): List<MessageMediaEntity>

    @Query("SELECT * FROM message_media WHERE message_id = :messageId ORDER BY position ASC")
    fun observeMediaForMessage(messageId: UUID): Flow<List<MessageMediaEntity>>

    @Query(
        """
        SELECT * FROM message_media
        WHERE message_id IN (:messageIds)
        ORDER BY message_id ASC, position ASC
        """,
    )
    suspend fun getMediaForMessages(
        messageIds: List<UUID>,
    ): List<MessageMediaEntity>

    @Query(
        """
        SELECT * FROM message_media
        WHERE message_id IN (:messageIds)
        ORDER BY message_id ASC, position ASC
        """,
    )
    fun observeMediaForMessages(
        messageIds: List<UUID>,
    ): Flow<List<MessageMediaEntity>>

    @Query(
        """
        UPDATE message_media
        SET upload_status = :status,
            upload_progress = :progress
        WHERE id = :mediaId
        """,
    )
    suspend fun updateUploadProgress(
        mediaId: UUID,
        status: MediaUploadStatus,
        progress: Int,
    )

    @Query(
        """
        UPDATE message_media
        SET storage_path = :storagePath,
            upload_status = :status,
            upload_progress = 100,
            last_upload_error = NULL
        WHERE id = :mediaId
        """,
    )
    suspend fun markUploadSucceeded(
        mediaId: UUID,
        storagePath: String,
        status: MediaUploadStatus,
    )

    @Query(
        """
        UPDATE message_media
        SET upload_status = :status,
            upload_attempt_count = :attemptCount,
            last_upload_error = :lastError
        WHERE id = :mediaId
        """,
    )
    suspend fun markUploadFailed(
        mediaId: UUID,
        status: MediaUploadStatus,
        attemptCount: Int,
        lastError: String?,
    )

    @Query(
        """
        SELECT * FROM message_media
        WHERE upload_status IN (:statuses)
        ORDER BY message_id ASC, position ASC
        """,
    )
    suspend fun getMediaByUploadStatuses(
        statuses: List<MediaUploadStatus>,
    ): List<MessageMediaEntity>

    @Query("DELETE FROM message_media WHERE message_id = :messageId")
    suspend fun deleteMediaForMessage(messageId: UUID)
}
