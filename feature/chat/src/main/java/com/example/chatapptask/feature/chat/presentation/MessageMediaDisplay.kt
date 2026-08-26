package com.example.chatapptask.feature.chat.presentation

import com.example.chatapptask.core.domain.model.MediaType
import com.example.chatapptask.core.domain.model.Message
import com.example.chatapptask.core.domain.model.MessageMedia
import com.example.chatapptask.core.domain.model.MessageSendStatus
import java.util.UUID

/**
 * Chat-bubble view of one attachment. [displayUri] is a local file URI or a public HTTP URL.
 */
data class MessageMediaItemUi(
    val id: UUID,
    val mediaType: MediaType,
    val position: Int,
    val displayUri: String?,
)

fun messageHasRenderableBody(message: Message): Boolean =
    !message.textContent.isNullOrBlank() || message.media.isNotEmpty()

fun messageMediaItemsForDisplay(
    message: Message,
    publicUrlFor: (storagePath: String) -> String?,
): List<MessageMediaItemUi> =
    message.media
        .sortedBy(MessageMedia::position)
        .map { media ->
            MessageMediaItemUi(
                id = media.id,
                mediaType = media.mediaType,
                position = media.position,
                displayUri = resolveMediaDisplayUri(media, message.sendStatus, publicUrlFor),
            )
        }

internal fun resolveMediaDisplayUri(
    media: MessageMedia,
    sendStatus: MessageSendStatus,
    publicUrlFor: (storagePath: String) -> String?,
): String? {
    val localUri = media.localUri?.trim()?.takeIf(String::isNotEmpty)
    val remoteUri = remoteDisplayUri(media.storagePath, publicUrlFor)
    return when (sendStatus) {
        MessageSendStatus.SENDING, MessageSendStatus.FAILED -> localUri ?: remoteUri
        MessageSendStatus.SENT -> remoteUri ?: localUri
    }
}

internal fun remoteDisplayUri(
    storagePath: String?,
    publicUrlFor: (storagePath: String) -> String?,
): String? {
    val value = storagePath?.trim()?.takeIf(String::isNotEmpty) ?: return null
    if (value.startsWith("https://", ignoreCase = true) ||
        value.startsWith("http://", ignoreCase = true)
    ) {
        return value
    }
    return publicUrlFor(value)?.trim()?.takeIf(String::isNotEmpty)
}
