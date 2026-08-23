package com.example.chatapptask.core.domain.model

import java.time.Instant
import java.util.UUID

data class Message(
    val id: UUID,
    val senderId: UUID,
    val textContent: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val media: List<MessageMedia>,
    val sendStatus: MessageSendStatus,
)
