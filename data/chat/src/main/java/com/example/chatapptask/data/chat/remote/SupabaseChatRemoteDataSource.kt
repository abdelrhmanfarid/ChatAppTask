package com.example.chatapptask.data.chat.remote

import com.example.chatapptask.core.domain.model.MediaUploadStatus
import com.example.chatapptask.core.domain.model.Message
import com.example.chatapptask.core.domain.model.MessageMedia
import com.example.chatapptask.core.domain.model.User
import com.example.chatapptask.core.network.dto.MessageDto
import com.example.chatapptask.core.network.dto.MessageMediaDto
import com.example.chatapptask.core.network.dto.RealtimeMessageIdDto
import com.example.chatapptask.core.network.dto.RealtimeMessageMediaMessageIdDto
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
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

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

    override suspend fun getMessage(messageId: UUID): Message? {
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

    override fun observeRemoteMessageIds(): Flow<UUID> = channelFlow {
        val channel = supabaseClient.channel(MESSAGES_REALTIME_CHANNEL)
        val messageChanges = channel.postgresChangeFlow<PostgresAction>(schema = PUBLIC_SCHEMA) {
            table = MESSAGES_TABLE
        }
        val mediaChanges = channel.postgresChangeFlow<PostgresAction>(schema = PUBLIC_SCHEMA) {
            table = MESSAGE_MEDIA_TABLE
        }
        val collector = launch {
            launch {
                messageChanges.collect { action ->
                    messageIdOf(action)?.let { send(it) }
                }
            }
            launch {
                mediaChanges.collect { action ->
                    messageIdFromMedia(action)?.let { send(it) }
                }
            }
        }
        try {
            channel.subscribe(blockUntilSubscribed = true)
            awaitCancellation()
        } finally {
            collector.cancel()
            runCatching { channel.unsubscribe() }
        }
    }

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
        // Deterministic `{messageId}/{mediaId}.{ext}` retry: overwrite the same object if
        // Storage succeeded but Room never recorded UPLOADED. Same pattern as profile avatars.
        supabaseClient.storage[CHAT_MEDIA_BUCKET].upload(
            path = storagePath,
            data = bytes,
        ) {
            upsert = true
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

    private fun messageIdOf(action: PostgresAction): UUID? =
        try {
            val id = when (action) {
                is PostgresAction.Insert -> action.decodeRecord<RealtimeMessageIdDto>().id
                is PostgresAction.Update -> action.decodeRecord<RealtimeMessageIdDto>().id
                is PostgresAction.Delete, is PostgresAction.Select -> return null
            }
            UUID.fromString(id)
        } catch (_: Exception) {
            null
        }

    private fun messageIdFromMedia(action: PostgresAction): UUID? =
        try {
            val messageId = when (action) {
                is PostgresAction.Insert -> action.decodeRecord<RealtimeMessageMediaMessageIdDto>().messageId
                is PostgresAction.Update -> action.decodeRecord<RealtimeMessageMediaMessageIdDto>().messageId
                is PostgresAction.Delete, is PostgresAction.Select -> return null
            }
            UUID.fromString(messageId)
        } catch (_: Exception) {
            null
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
        const val MESSAGES_REALTIME_CHANNEL = "public:messages"
        const val PUBLIC_SCHEMA = "public"
        const val CREATE_MEDIA_MESSAGE_RPC = "create_media_message"
        const val CHAT_MEDIA_BUCKET = "chat-media"
        const val PROFILE_IMAGES_BUCKET = "profile-images"

        val FILE_EXTENSION_PATTERN = Regex("[a-z0-9]+")
    }
}
