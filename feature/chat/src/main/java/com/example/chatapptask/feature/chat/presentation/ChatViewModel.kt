package com.example.chatapptask.feature.chat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapptask.core.domain.repository.ChatRepository
import com.example.chatapptask.core.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<ChatEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            try {
                val currentUserId = userRepository.getCurrentUserId()
                _uiState.update { state -> state.copy(currentUserId = currentUserId) }
            } catch (exception: Exception) {
                eventChannel.send(
                    ChatEvent.ShowError(exception.message ?: IDENTITY_ERROR_MESSAGE),
                )
            }
        }

        viewModelScope.launch {
            chatRepository.observeMessages().collect { messages ->
                _uiState.update { state -> state.copy(messages = messages) }
            }
        }
    }

    fun onAction(action: ChatAction) {
        when (action) {
            is ChatAction.ComposerTextChanged -> {
                _uiState.update { state -> state.copy(composerText = action.text) }
            }

            ChatAction.SendText -> sendText()
            is ChatAction.RetryMessage -> retryMessage(action.messageId)
        }
    }

    private fun sendText() {
        val text = uiState.value.composerText.trim()
        if (text.isEmpty() || uiState.value.isSendRequestInProgress) return

        _uiState.update { state -> state.copy(isSendRequestInProgress = true) }
        viewModelScope.launch {
            try {
                chatRepository.sendTextMessage(text)
                _uiState.update { state ->
                    state.copy(
                        composerText = "",
                        isSendRequestInProgress = false,
                    )
                }
            } catch (exception: Exception) {
                _uiState.update { state -> state.copy(isSendRequestInProgress = false) }
                eventChannel.send(
                    ChatEvent.ShowError(exception.message ?: SEND_ERROR_MESSAGE),
                )
            }
        }
    }

    private fun retryMessage(messageId: UUID) {
        viewModelScope.launch {
            try {
                chatRepository.retryMessage(messageId)
            } catch (exception: Exception) {
                eventChannel.send(
                    ChatEvent.ShowError(exception.message ?: RETRY_ERROR_MESSAGE),
                )
            }
        }
    }

    private companion object {
        const val SEND_ERROR_MESSAGE = "Unable to schedule the message."
        const val RETRY_ERROR_MESSAGE = "Unable to schedule the retry."
        const val IDENTITY_ERROR_MESSAGE = "Unable to identify the current user."
    }
}
