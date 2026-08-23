package com.example.chatapptask.feature.chat.presentation

import com.example.chatapptask.core.domain.model.Message
import java.util.UUID

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val composerText: String = "",
    val isSendRequestInProgress: Boolean = false,
)

sealed interface ChatAction {
    data class ComposerTextChanged(val text: String) : ChatAction

    data object SendText : ChatAction

    data class RetryMessage(val messageId: UUID) : ChatAction
}

sealed interface ChatEvent {
    data class ShowError(val message: String) : ChatEvent
}
