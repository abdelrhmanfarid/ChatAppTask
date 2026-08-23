package com.example.chatapptask.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageDto(
    @SerialName("id")
    val id: String,
    @SerialName("sender_id")
    val senderId: String,
    @SerialName("text_content")
    val textContent: String?,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
)

@Serializable
data class TextMessageInsertDto(
    @SerialName("id")
    val id: String,
    @SerialName("sender_id")
    val senderId: String,
    @SerialName("text_content")
    val textContent: String,
)
