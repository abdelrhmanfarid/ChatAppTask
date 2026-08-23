package com.example.chatapptask.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.chatapptask.core.domain.model.MessageSendStatus
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["sender_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["sender_id"]),
        Index(value = ["created_at", "id"]),
    ],
)
data class MessageEntity(
    @PrimaryKey
    val id: UUID,
    @ColumnInfo(name = "sender_id")
    val senderId: UUID,
    @ColumnInfo(name = "text_content")
    val textContent: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
    @ColumnInfo(name = "send_status")
    val sendStatus: MessageSendStatus,
    @ColumnInfo(name = "send_attempt_count")
    val sendAttemptCount: Int = 0,
    @ColumnInfo(name = "last_send_error")
    val lastSendError: String? = null,
)
