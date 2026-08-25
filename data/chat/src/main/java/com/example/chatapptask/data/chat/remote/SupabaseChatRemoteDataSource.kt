package com.example.chatapptask.data.chat.remote

import com.example.chatapptask.core.domain.model.MediaUploadStatus
import com.example.chatapptask.core.domain.model.Message
import com.example.chatapptask.core.domain.model.MessageMedia
import com.example.chatapptask.core.domain.model.User
import com.example.chatapptask.core.network.dto.MessageDto
import com.example.chatapptask.core.network.dto.MessageMediaDto
import com.example.chatapptask.core.network.dto.UserDto
import com.example.chatapptask.core.network.mapper.createMediaMessageParams
import com.example.chatapptask.core.network.mapper.createTextMessageInsertDto
import com.example.chatapptask.core.network.mapper.toDomain
import com.example.chatapptask.core.network.mapper.toUpsertDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class SupabaseChatRemoteDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient,
) : ChatRemoteDataSource {
    override suspend fun upsertUser(user: User): User =
        supabaseClient
            .from(USERS_TABLE)
            .upsert(user.toUpsertDto()) {
                select()
            }
            .decodeSingle<UserDto>()
            .toDomain()

    override suspend fun getUser(userId: UUID): User? =
        supabaseClient
            .from(USERS_TABLE)
            .select {
                filter {
                    eq("id", userId.toString())
                }
                limit(1)
            }
            .decodeSingleOrNull<UserDto>()
            ?.toDomain()

    override suspend fun getLatestMessages(limit: Int): List<Message> {
        requirePositiveLimit(limit)
        val messages = supabaseClient
            .from(MESSAGES_TABLE)
            .select {
                order("created_at", Order.DESCENDING)
                order("id", Order.DESCENDING)
                limit(limit.toLong())
            }
            .decodeList<MessageDto>()
        return mapMessagesWithMedia(messages)
    }

    override suspend fun getOlderMessages(
        cursorCreatedAt: Instant,
        cursorMessageId: UUID,
        limit: Int,
    ): List<Message> {
        requirePositiveLimit(limit)
        val cursorTimestamp = cursorCreatedAt.toString()
        val messages = supabaseClient
            .from(MESSAGES_TABLE)
            .select {
                filter {
                    or {
                        lt("created_at", cursorTimestamp)
                        and {
                            eq("created_at", cursorTimestamp)
                            lt("id", cursorMessageId.toString())
                        }
                    }
                }
                order("created_at", Order.DESCENDING)
                order("id", Order.DESCENDING)
                limit(limit.toLong())
            }
            .decodeList<MessageDto>()
        return mapMessagesWithMedia(messages)
    }

    override suspend fun insertTextMessage(
        messageId: UUID,
        senderId: UUID,
        text: String,
    ): Message =
        supabaseClient
            .from(MESSAGES_TABLE)
            .insert(
                createTextMessageInsertDto(
                    messageId = messageId,
                    senderId = senderId,
                    text = text,
                ),
            ) {
                select()
            }
            .decodeSingle<MessageDto>()
            .toDomain(media = emptyList())

    override suspend fun createMediaMessage(
        messageId: UUID,
        senderId: UUID,
        text: String?,
        media: List<MessageMedia>,
    ): Message {
        require(media.all { item -> item.uploadStatus == MediaUploadStatus.UPLOADED }) {
            "create_media_message requires every media item to be uploaded."
        }
        val returnedMessageId = supabaseClient.postgrest
            .rpc(
                function = CREATE_MEDIA_MESSAGE_RPC,
                parameters = createMediaMessageParams(
                    messageId = messageId,
                    senderId = senderId,
                    text = text,
                    media = media,
                ),
            )
            .decodeAs<String>()
            .toUuidOrThrow("create_media_message returned an invalid message UUID")

        return getMessage(returnedMessageId)
            ?: error("create_media_message succeeded, but message $returnedMessageId was not persisted.")
    }

    override suspend fun uploadChatMedia(
        messageId: UUID,
        mediaId: UUID,
        extension: String,
        bytes: ByteArray,
        mimeType: String,
    ): String {
        val normalizedExtension = normalizeExtension(extension)
        val storagePath = "$messageId/$mediaId.$normalizedExtension"
        supabaseClient.storage[CHAT_MEDIA_BUCKET].upload(
            path = storagePath,
            data = bytes,
        ) {
            contentType = ContentType.parse(mimeType)
        }
        return storagePath
    }

    override suspend fun uploadProfileImage(
        userId: UUID,
        bytes: ByteArray,
        mimeType: String,
        fileExtension: String,
    ): String {
        val normalizedExtension = normalizeExtension(fileExtension)
        val storagePath = "$userId/avatar.$normalizedExtension"
        supabaseClient.storage[PROFILE_IMAGES_BUCKET].upload(
            path = storagePath,
            data = bytes,
        ) {
            upsert = true
            contentType = ContentType.parse(mimeType)
        }
        return storagePath
    }

    override suspend fun deleteChatMediaObject(storagePath: String) {
        supabaseClient.storage[CHAT_MEDIA_BUCKET].delete(validateChatMediaPath(storagePath))
    }

    private suspend fun getMessage(messageId: UUID): Message? {
        val message = supabaseClient
            .from(MESSAGES_TABLE)
            .select {
                filter {
                    eq("id", messageId.toString())
                }
                limit(1)
            }
            .decodeSingleOrNull<MessageDto>()
            ?: return null
        val media = getMediaForMessages(listOf(messageId))
        return message.toDomain(media)
    }

    private suspend fun mapMessagesWithMedia(messages: List<MessageDto>): List<Message> {
        if (messages.isEmpty()) return emptyList()

        val mediaByMessageId = getMediaForMessages(
            messages.map { message -> UUID.fromString(message.id) },
        ).groupBy { media -> UUID.fromString(media.messageId) }

        return messages.map { message ->
            message.toDomain(mediaByMessageId[UUID.fromString(message.id)].orEmpty())
        }
    }

    private suspend fun getMediaForMessages(messageIds: List<UUID>): List<MessageMediaDto> {
        if (messageIds.isEmpty()) return emptyList()

        return supabaseClient
            .from(MESSAGE_MEDIA_TABLE)
            .select {
                filter {
                    isIn("message_id", messageIds.map(UUID::toString))
                }
                order("message_id", Order.ASCENDING)
                order("position", Order.ASCENDING)
            }
            .decodeList()
    }

    private fun requirePositiveLimit(limit: Int) {
        require(limit > 0) { "Message page limit must be greater than zero." }
    }

    private fun normalizeExtension(extension: String): String {
        val normalized = extension.trim().removePrefix(".").lowercase()
        require(normalized.matches(FILE_EXTENSION_PATTERN)) {
            "Media extension must contain only letters and digits."
        }
        return normalized
    }

    private fun validateChatMediaPath(storagePath: String): String {
        val normalized = storagePath.trim()
        val parts = normalized.split('/')
        require(parts.size == 2) { "Chat media storage path must contain exactly one directory." }
        parts[0].toUuidOrThrow("Chat media storage path has an invalid message UUID")

        val fileNameParts = parts[1].split('.', limit = 2)
        require(fileNameParts.size == 2) { "Chat media storage path must include a file extension." }
        fileNameParts[0].toUuidOrThrow("Chat media storage path has an invalid media UUID")
        normalizeExtension(fileNameParts[1])
        return normalized
    }

    private fun String.toUuidOrThrow(message: String): UUID =
        try {
            UUID.fromString(this)
        } catch (exception: IllegalArgumentException) {
            throw IllegalStateException(message, exception)
        }

    private companion object {
        const val USERS_TABLE = "users"
        const val MESSAGES_TABLE = "messages"
        const val MESSAGE_MEDIA_TABLE = "message_media"
        const val CREATE_MEDIA_MESSAGE_RPC = "create_media_message"
        const val CHAT_MEDIA_BUCKET = "chat-media"
        const val PROFILE_IMAGES_BUCKET = "profile-images"

        val FILE_EXTENSION_PATTERN = Regex("[a-z0-9]+")
    }
}
