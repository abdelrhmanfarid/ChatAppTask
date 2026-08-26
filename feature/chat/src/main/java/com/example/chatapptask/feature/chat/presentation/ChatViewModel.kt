package com.example.chatapptask.feature.chat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapptask.core.domain.ChatMediaPublicUrlFactory
import com.example.chatapptask.core.domain.ProfileImagePublicUrlFactory
import com.example.chatapptask.core.domain.model.MessageSendStatus
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
    private val chatMediaPublicUrlFactory: ChatMediaPublicUrlFactory =
        ChatMediaPublicUrlFactory { null },
    private val profileImagePublicUrlFactory: ProfileImagePublicUrlFactory =
        ProfileImagePublicUrlFactory { null },
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

        viewModelScope.launch {
            userRepository.observeUsers().collect { users ->
                _uiState.update { state ->
                    state.copy(sendersById = users.associateBy { user -> user.id })
                }
            }
        }

        viewModelScope.launch {
            try {
                chatRepository.startRealtimeSync()
            } catch (exception: Exception) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                eventChannel.send(
                    ChatEvent.ShowError(exception.message ?: REALTIME_ERROR_MESSAGE),
                )
            }
        }

        viewModelScope.launch {
            try {
                chatRepository.loadLatestMessages()
            } catch (exception: Exception) {
                eventChannel.send(
                    ChatEvent.ShowError(exception.message ?: LOAD_ERROR_MESSAGE),
                )
            }
        }
    }

    internal fun publicChatMediaUrl(storagePath: String): String? =
        chatMediaPublicUrlFactory.publicUrlFor(storagePath)

    internal fun publicProfileImageUrl(profileImagePath: String?): String? =
        profileImagePublicUrlFactory.publicUrlFor(profileImagePath)

    fun onAction(action: ChatAction) {
        when (action) {
            is ChatAction.ComposerTextChanged -> {
                _uiState.update { state -> state.copy(composerText = action.text) }
            }

            ChatAction.SendText -> sendMessage()
            ChatAction.AttachmentClicked -> requestMediaPicker()
            is ChatAction.MediaSelected -> appendSelectedMedia(action.attachments)
            is ChatAction.RemoveSelectedMedia -> {
                _uiState.update { state ->
                    state.copy(
                        selectedAttachments = state.selectedAttachments.filterNot { attachment ->
                            attachment.uri == action.uri
                        },
                    )
                }
            }

            is ChatAction.RetryMessage -> retryMessage(action.messageId)
            ChatAction.LoadOlderMessages -> loadOlderMessages()
        }
    }

    private fun requestMediaPicker() {
        val remaining = uiState.value.remainingAttachmentSlots
        if (remaining <= 0) {
            viewModelScope.launch {
                eventChannel.send(ChatEvent.ShowError(ATTACHMENT_LIMIT_MESSAGE))
            }
            return
        }
        viewModelScope.launch {
            eventChannel.send(ChatEvent.OpenMediaPicker(maxItems = remaining))
        }
    }

    private fun appendSelectedMedia(incoming: List<ComposerAttachment>) {
        if (incoming.isEmpty()) return
        _uiState.update { state ->
            val existingUris = state.selectedAttachments.mapTo(HashSet()) { it.uri }
            val remaining = MAX_COMPOSER_ATTACHMENTS - state.selectedAttachments.size
            if (remaining <= 0) return@update state

            val accepted = incoming
                .asSequence()
                .filter { attachment -> attachment.uri !in existingUris }
                .distinctBy { attachment -> attachment.uri }
                .take(remaining)
                .toList()
            if (accepted.isEmpty()) return@update state

            state.copy(selectedAttachments = state.selectedAttachments + accepted)
        }
    }

    private fun loadOlderMessages() {
        var shouldLoad = false
        _uiState.update { state ->
            if (state.isLoadingOlder || !state.hasMoreOlderMessages) return@update state
            if (state.messages.none { message -> message.sendStatus == MessageSendStatus.SENT }) {
                return@update state
            }
            shouldLoad = true
            state.copy(isLoadingOlder = true)
        }
        if (!shouldLoad) return

        viewModelScope.launch {
            try {
                val pageSize = chatRepository.loadOlderMessages(limit = OLDER_PAGE_SIZE)
                _uiState.update { state ->
                    state.copy(
                        isLoadingOlder = false,
                        hasMoreOlderMessages = pageSize >= OLDER_PAGE_SIZE,
                    )
                }
            } catch (exception: Exception) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                _uiState.update { state -> state.copy(isLoadingOlder = false) }
                eventChannel.send(
                    ChatEvent.ShowError(exception.message ?: LOAD_OLDER_ERROR_MESSAGE),
                )
            }
        }
    }

    private fun sendMessage() {
        val state = uiState.value
        if (state.isSendRequestInProgress) return

        if (state.selectedAttachments.isNotEmpty()) {
            sendMediaMessage(
                attachments = state.selectedAttachments,
                text = state.composerText.trim().ifBlank { null },
            )
            return
        }

        val text = state.composerText.trim()
        if (text.isEmpty()) return
        sendTextMessage(text)
    }

    private fun sendTextMessage(text: String) {
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

    private fun sendMediaMessage(
        attachments: List<ComposerAttachment>,
        text: String?,
    ) {
        _uiState.update { state -> state.copy(isSendRequestInProgress = true) }
        viewModelScope.launch {
            try {
                chatRepository.sendMediaMessage(
                    media = attachments.map(ComposerAttachment::toPendingMedia),
                    text = text,
                )
                _uiState.update { state ->
                    state.copy(
                        composerText = "",
                        selectedAttachments = emptyList(),
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
        const val LOAD_ERROR_MESSAGE = "Unable to load messages."
        const val LOAD_OLDER_ERROR_MESSAGE = "Unable to load older messages."
        const val REALTIME_ERROR_MESSAGE = "Unable to start live message updates."
        const val ATTACHMENT_LIMIT_MESSAGE = "You can attach up to 10 photos or videos."
        const val OLDER_PAGE_SIZE = 20
    }
}
