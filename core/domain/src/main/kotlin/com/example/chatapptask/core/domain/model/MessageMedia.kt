package com.example.chatapptask.core.domain.model

import java.util.UUID

data class MessageMedia(
    val id: UUID,
    val messageId: UUID,
    val storagePath: String?,
    val mediaType: MediaType,
    val mimeType: String,
    val position: Int,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    val localUri: String?,
    val uploadStatus: MediaUploadStatus,
)
