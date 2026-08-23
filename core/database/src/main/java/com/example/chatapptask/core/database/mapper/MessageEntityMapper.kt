package com.example.chatapptask.core.database.mapper

import com.example.chatapptask.core.database.entity.MessageEntity
import com.example.chatapptask.core.database.entity.MessageMediaEntity
import com.example.chatapptask.core.domain.model.Message

fun MessageEntity.toDomain(media: List<MessageMediaEntity>): Message =
    Message(
        id = id,
        senderId = senderId,
        textContent = textContent,
        createdAt = createdAt,
        updatedAt = updatedAt,
        media = media.sortedBy(MessageMediaEntity::position).map(MessageMediaEntity::toDomain),
        sendStatus = sendStatus,
    )

/**
 * Creates a fresh local row. Existing send retry state must be preserved through focused DAO
 * updates rather than replacing an existing row with this mapping.
 */
fun Message.toEntity(): MessageEntity =
    MessageEntity(
        id = id,
        senderId = senderId,
        textContent = textContent,
        createdAt = createdAt,
        updatedAt = updatedAt,
        sendStatus = sendStatus,
        sendAttemptCount = 0,
        lastSendError = null,
    )
