package com.example.chatapptask.core.database.mapper

import com.example.chatapptask.core.database.entity.MessageMediaEntity
import com.example.chatapptask.core.domain.model.MessageMedia

fun MessageMediaEntity.toDomain(): MessageMedia =
    MessageMedia(
        id = id,
        messageId = messageId,
        storagePath = storagePath,
        mediaType = mediaType,
        mimeType = mimeType,
        position = position,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        localUri = localUri,
        uploadStatus = uploadStatus,
    )

/**
 * Creates a fresh local row. Existing upload progress and retry state must be preserved through
 * focused DAO updates rather than replacing an existing row with this mapping.
 */
fun MessageMedia.toEntity(): MessageMediaEntity =
    MessageMediaEntity(
        id = id,
        messageId = messageId,
        storagePath = storagePath,
        mediaType = mediaType,
        mimeType = mimeType,
        position = position,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        createdAt = null,
        localUri = localUri,
        uploadStatus = uploadStatus,
        uploadProgress = 0,
        uploadAttemptCount = 0,
        lastUploadError = null,
    )
