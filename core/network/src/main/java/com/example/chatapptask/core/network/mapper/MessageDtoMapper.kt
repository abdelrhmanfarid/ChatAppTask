package com.example.chatapptask.core.network.mapper

import com.example.chatapptask.core.domain.model.Message
import com.example.chatapptask.core.domain.model.MessageMedia
import com.example.chatapptask.core.domain.model.MessageSendStatus
import com.example.chatapptask.core.network.dto.CreateMediaMessageParams
import com.example.chatapptask.core.network.dto.MessageDto
import com.example.chatapptask.core.network.dto.MessageMediaDto
import com.example.chatapptask.core.network.dto.TextMessageInsertDto
import java.time.Instant
import java.util.UUID

fun MessageDto.toDomain(media: List<MessageMediaDto>): Message =
    Message(
        id = UUID.fromString(id),
        senderId = UUID.fromString(senderId),
        textContent = textContent,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
        media = media.sortedBy(MessageMediaDto::position).map(MessageMediaDto::toDomain),
        sendStatus = MessageSendStatus.SENT,
    )

fun createTextMessageInsertDto(
    messageId: UUID,
    senderId: UUID,
    text: String,
): TextMessageInsertDto =
    TextMessageInsertDto(
        id = messageId.toString(),
        senderId = senderId.toString(),
        textContent = text,
    )

fun createMediaMessageParams(
    messageId: UUID,
    senderId: UUID,
    text: String?,
    media: List<MessageMedia>,
): CreateMediaMessageParams =
    CreateMediaMessageParams(
        messageId = messageId.toString(),
        senderId = senderId.toString(),
        textContent = text,
        media = media.map(MessageMedia::toCreateMediaMessageItemDto),
    )
