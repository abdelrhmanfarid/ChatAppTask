package com.example.chatapptask.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageMediaDto(
    @SerialName("id")
    val id: String,
    @SerialName("message_id")
    val messageId: String,
    @SerialName("storage_path")
    val storagePath: String,
    @SerialName("media_type")
    val mediaType: String,
    @SerialName("mime_type")
    val mimeType: String,
    @SerialName("position")
    val position: Int,
    @SerialName("size_bytes")
    val sizeBytes: Long?,
    @SerialName("width")
    val width: Int?,
    @SerialName("height")
    val height: Int?,
    @SerialName("created_at")
    val createdAt: String,
)
