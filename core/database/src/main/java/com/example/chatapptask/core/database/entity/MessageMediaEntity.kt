package com.example.chatapptask.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.chatapptask.core.domain.model.MediaType
import com.example.chatapptask.core.domain.model.MediaUploadStatus
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "message_media",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["message_id"]),
        Index(value = ["message_id", "position"], unique = true),
    ],
)
data class MessageMediaEntity(
    @PrimaryKey
    val id: UUID,
    @ColumnInfo(name = "message_id")
    val messageId: UUID,
    @ColumnInfo(name = "storage_path")
    val storagePath: String?,
    @ColumnInfo(name = "media_type")
    val mediaType: MediaType,
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    val position: Int,
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant?,
    @ColumnInfo(name = "local_uri")
    val localUri: String?,
    @ColumnInfo(name = "upload_status")
    val uploadStatus: MediaUploadStatus,
    @ColumnInfo(name = "upload_progress")
    val uploadProgress: Int = 0,
    @ColumnInfo(name = "upload_attempt_count")
    val uploadAttemptCount: Int = 0,
    @ColumnInfo(name = "last_upload_error")
    val lastUploadError: String? = null,
)
