package com.example.chatapptask.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateMediaMessageItemDto(
    @SerialName("id")
    val id: String,
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
)

@Serializable
data class CreateMediaMessageParams(
    @SerialName("p_message_id")
    val messageId: String,
    @SerialName("p_sender_id")
    val senderId: String,
    @SerialName("p_text_content")
    val textContent: String?,
    @SerialName("p_media")
    val media: List<CreateMediaMessageItemDto>,
)
