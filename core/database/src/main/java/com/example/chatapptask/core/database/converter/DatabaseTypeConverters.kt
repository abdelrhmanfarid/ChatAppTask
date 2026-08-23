package com.example.chatapptask.core.database.converter

import androidx.room.TypeConverter
import com.example.chatapptask.core.domain.model.MediaType
import com.example.chatapptask.core.domain.model.MediaUploadStatus
import com.example.chatapptask.core.domain.model.MessageSendStatus
import java.time.Instant
import java.util.UUID

class DatabaseTypeConverters {
    @TypeConverter
    fun uuidToString(value: UUID): String = value.toString()

    @TypeConverter
    fun stringToUuid(value: String): UUID = UUID.fromString(value)

    @TypeConverter
    fun instantToString(value: Instant?): String? = value?.toString()

    @TypeConverter
    fun stringToInstant(value: String?): Instant? = value?.let(Instant::parse)

    @TypeConverter
    fun messageSendStatusToString(value: MessageSendStatus): String = value.name

    @TypeConverter
    fun stringToMessageSendStatus(value: String): MessageSendStatus =
        MessageSendStatus.valueOf(value)

    @TypeConverter
    fun mediaUploadStatusToString(value: MediaUploadStatus): String = value.name

    @TypeConverter
    fun stringToMediaUploadStatus(value: String): MediaUploadStatus =
        MediaUploadStatus.valueOf(value)

    @TypeConverter
    fun mediaTypeToString(value: MediaType): String = value.name

    @TypeConverter
    fun stringToMediaType(value: String): MediaType = MediaType.valueOf(value)
}
