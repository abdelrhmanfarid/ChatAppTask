package com.example.chatapptask.core.domain.model

data class PendingMedia(
    val localUri: String,
    val mediaType: MediaType,
    val mimeType: String,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
)
