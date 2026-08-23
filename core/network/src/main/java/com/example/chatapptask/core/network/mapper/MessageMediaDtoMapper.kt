package com.example.chatapptask.core.network.mapper

import com.example.chatapptask.core.domain.model.MediaType
import com.example.chatapptask.core.domain.model.MediaUploadStatus
import com.example.chatapptask.core.domain.model.MessageMedia
import com.example.chatapptask.core.network.dto.CreateMediaMessageItemDto
import com.example.chatapptask.core.network.dto.MessageMediaDto
import java.util.UUID

fun MessageMediaDto.toDomain(): MessageMedia =
    MessageMedia(
        id = UUID.fromString(id),
        messageId = UUID.fromString(messageId),
        storagePath = storagePath,
        mediaType = mediaType.toDomainMediaType(),
        mimeType = mimeType,
        position = position,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        localUri = null,
        uploadStatus = MediaUploadStatus.UPLOADED,
    )

fun MessageMedia.toCreateMediaMessageItemDto(): CreateMediaMessageItemDto =
    CreateMediaMessageItemDto(
        id = id.toString(),
        storagePath = requireNotNull(storagePath) {
            "Cannot create a remote media payload for media $id without a storage path."
        },
        mediaType = mediaType.toRemoteValue(),
        mimeType = mimeType,
        position = position,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
    )

private fun String.toDomainMediaType(): MediaType =
    when (this) {
        "image" -> MediaType.IMAGE
        "video" -> MediaType.VIDEO
        else -> error("Unsupported remote media type: $this")
    }

private fun MediaType.toRemoteValue(): String =
    when (this) {
        MediaType.IMAGE -> "image"
        MediaType.VIDEO -> "video"
    }
