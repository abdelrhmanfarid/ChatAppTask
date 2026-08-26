package com.example.chatapptask.feature.chat.presentation

import com.example.chatapptask.core.domain.model.MediaType
import com.example.chatapptask.core.domain.model.Message
import com.example.chatapptask.core.domain.model.PendingMedia
import java.util.UUID

const val MAX_COMPOSER_ATTACHMENTS = 10

/**
 * Temporary composer selection until Send. Not persisted.
 * Holds only what preview and [PendingMedia] construction need.
 */
data class ComposerAttachment(
    val uri: String,
    val mediaType: MediaType,
    val mimeType: String,
    val sizeBytes: Long? = null,
) {
    fun toPendingMedia(): PendingMedia =
        PendingMedia(
            localUri = uri,
            mediaType = mediaType,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            width = null,
            height = null,
        )
}

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val currentUserId: UUID? = null,
    val composerText: String = "",
    val selectedAttachments: List<ComposerAttachment> = emptyList(),
    val isSendRequestInProgress: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val hasMoreOlderMessages: Boolean = true,
) {
    val remainingAttachmentSlots: Int
        get() = (MAX_COMPOSER_ATTACHMENTS - selectedAttachments.size).coerceAtLeast(0)

    val canSend: Boolean
        get() = !isSendRequestInProgress &&
            (selectedAttachments.isNotEmpty() || composerText.isNotBlank())
}

sealed interface ChatAction {
    data class ComposerTextChanged(val text: String) : ChatAction

    /** Send button / IME Send: text-only or media (+ optional text). */
    data object SendText : ChatAction

    data object AttachmentClicked : ChatAction

    data class MediaSelected(val attachments: List<ComposerAttachment>) : ChatAction

    data class RemoveSelectedMedia(val uri: String) : ChatAction

    data class RetryMessage(val messageId: UUID) : ChatAction

    data object LoadOlderMessages : ChatAction
}

sealed interface ChatEvent {
    data class ShowError(val message: String) : ChatEvent

    /** Launch Photo Picker at the Compose boundary; [maxItems] is remaining capacity (1..10). */
    data class OpenMediaPicker(val maxItems: Int) : ChatEvent
}
