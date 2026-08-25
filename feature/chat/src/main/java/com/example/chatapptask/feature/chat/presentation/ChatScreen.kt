package com.example.chatapptask.feature.chat.presentation

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.chatapptask.core.domain.model.MediaType
import com.example.chatapptask.core.domain.model.Message
import com.example.chatapptask.core.domain.model.MessageSendStatus
import com.example.chatapptask.core.ui.clearFocusOnTap
import com.example.chatapptask.feature.chat.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun ChatRoute(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val unsupportedMediaMessage = stringResource(R.string.chat_attachment_unsupported)

    fun handlePickedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val resolved = resolveComposerAttachments(uris, context.contentResolver)
        if (resolved.attachments.isNotEmpty()) {
            viewModel.onAction(ChatAction.MediaSelected(resolved.attachments))
        }
        if (resolved.skippedUnsupportedCount > 0) {
            scope.launch {
                snackbarHostState.showSnackbar(unsupportedMediaMessage)
            }
        }
    }

    val multiMediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(
            maxItems = MAX_COMPOSER_ATTACHMENTS,
        ),
    ) { uris -> handlePickedUris(uris) }

    val singleMediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> handlePickedUris(listOfNotNull(uri)) }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is ChatEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                    is ChatEvent.OpenMediaPicker -> {
                        val request = PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageAndVideo,
                        )
                        if (event.maxItems <= 1) {
                            singleMediaPicker.launch(request)
                        } else {
                            multiMediaPicker.launch(request)
                        }
                    }
                }
            }
        }
    }

    ChatScreen(
        state = state,
        onAction = viewModel::onAction,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onAction: (ChatAction) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.chat_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            ChatComposer(
                text = state.composerText,
                selectedAttachments = state.selectedAttachments,
                isSending = state.isSendRequestInProgress,
                canSend = state.canSend,
                onTextChanged = { text ->
                    onAction(ChatAction.ComposerTextChanged(text))
                },
                onSend = { onAction(ChatAction.SendText) },
                onAttachmentClick = { onAction(ChatAction.AttachmentClicked) },
                onRemoveAttachment = { uri ->
                    onAction(ChatAction.RemoveSelectedMedia(uri))
                },
                modifier = Modifier.imePadding(),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val listState = rememberLazyListState()
        val newestMessage = state.messages.firstOrNull()
        var previousNewestMessageId by remember { mutableStateOf<UUID?>(null) }
        val reachedOldestLoaded by remember(state.messages.size) {
            derivedStateOf {
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo
                    .maxOfOrNull { item -> item.index }
                    ?: return@derivedStateOf false
                val oldestMessageIndex = state.messages.lastIndex
                oldestMessageIndex >= 0 && lastVisibleIndex >= oldestMessageIndex
            }
        }

        LaunchedEffect(newestMessage?.id, newestMessage?.sendStatus, state.currentUserId) {
            val shouldScroll = shouldScrollToOutgoingOptimisticMessage(
                previousNewestMessageId = previousNewestMessageId,
                newestMessage = newestMessage,
                currentUserId = state.currentUserId,
            )
            previousNewestMessageId = newestMessage?.id
            if (shouldScroll) {
                listState.animateScrollToItem(0)
            }
        }

        LaunchedEffect(
            reachedOldestLoaded,
            state.hasMoreOlderMessages,
            state.isLoadingOlder,
            state.messages.size,
        ) {
            if (
                reachedOldestLoaded &&
                state.hasMoreOlderMessages &&
                !state.isLoadingOlder &&
                state.messages.isNotEmpty()
            ) {
                onAction(ChatAction.LoadOlderMessages)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .clearFocusOnTap(),
        ) {
            if (state.messages.isEmpty()) {
                EmptyChatState(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = state.messages,
                        key = { message -> message.id },
                    ) { message ->
                        MessageBubble(
                            message = message,
                            isOutgoing = state.currentUserId == message.senderId,
                            onRetry = { onAction(ChatAction.RetryMessage(message.id)) },
                        )
                    }
                    if (state.isLoadingOlder) {
                        item(key = OLDER_LOADING_ITEM_KEY) {
                            OlderMessagesLoadingIndicator(
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    isOutgoing: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bubbleColor = if (isOutgoing) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = bubbleShape,
            color = bubbleColor,
            contentColor = contentColor,
            tonalElevation = if (isOutgoing) 0.dp else 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                message.textContent?.takeIf(String::isNotBlank)?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = message.createdAt.toDisplayTime(),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.72f),
                    )
                    if (isOutgoing) {
                        MessageSendState(
                            status = message.sendStatus,
                            onRetry = onRetry,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageSendState(
    status: MessageSendStatus,
    onRetry: () -> Unit,
) {
    when (status) {
        MessageSendStatus.SENDING -> {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
            )
            Text(
                text = stringResource(R.string.chat_sending),
                style = MaterialTheme.typography.labelSmall,
            )
        }

        MessageSendStatus.SENT -> Text(
            text = stringResource(R.string.chat_sent),
            style = MaterialTheme.typography.labelSmall,
        )

        MessageSendStatus.FAILED -> {
            Text(
                text = stringResource(R.string.chat_failed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(
                onClick = onRetry,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.chat_retry),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
fun ChatComposer(
    text: String,
    selectedAttachments: List<ComposerAttachment>,
    isSending: Boolean,
    canSend: Boolean,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onAttachmentClick: () -> Unit,
    onRemoveAttachment: (uri: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val attachmentDescription = stringResource(R.string.chat_attachment)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ComposerAttachmentPreviewRow(
                attachments = selectedAttachments,
                onRemove = onRemoveAttachment,
                enabled = !isSending,
            )
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedIconButton(
                    onClick = onAttachmentClick,
                    enabled = !isSending,
                    modifier = Modifier.semantics {
                        contentDescription = attachmentDescription
                    },
                ) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.chat_composer_placeholder)) },
                    minLines = 1,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { if (canSend) onSend() },
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onSend,
                    enabled = canSend,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.chat_send))
                    }
                }
            }
        }
    }
}

@Composable
private fun OlderMessagesLoadingIndicator(modifier: Modifier = Modifier) {
    val loadingDescription = stringResource(R.string.chat_loading_older)
    Box(
        modifier = modifier
            .padding(vertical = 8.dp)
            .semantics { contentDescription = loadingDescription },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
    }
}

@Composable
private fun EmptyChatState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.chat_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.chat_empty_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val OLDER_LOADING_ITEM_KEY = "older-messages-loading"

internal fun shouldScrollToOutgoingOptimisticMessage(
    previousNewestMessageId: UUID?,
    newestMessage: Message?,
    currentUserId: UUID?,
): Boolean {
    if (newestMessage == null || currentUserId == null) return false
    if (newestMessage.id == previousNewestMessageId) return false
    if (newestMessage.senderId != currentUserId) return false
    return newestMessage.sendStatus == MessageSendStatus.SENDING
}

private fun Instant.toDisplayTime(): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .format(this)

private val previewCurrentUserId = UUID.fromString("33eed91f-846c-49c8-851d-bca519b01432")
private val previewOtherUserId = UUID.fromString("44eed91f-846c-49c8-851d-bca519b01432")

private val previewState = ChatUiState(
    currentUserId = previewCurrentUserId,
    selectedAttachments = listOf(
        ComposerAttachment(
            uri = "content://preview/image-1",
            mediaType = MediaType.IMAGE,
            mimeType = "image/jpeg",
        ),
        ComposerAttachment(
            uri = "content://preview/video-1",
            mediaType = MediaType.VIDEO,
            mimeType = "video/mp4",
        ),
    ),
    messages = listOf(
        previewMessage(
            id = "00000000-0000-0000-0000-000000000004",
            senderId = previewCurrentUserId,
            text = "I’ll send the details shortly.",
            status = MessageSendStatus.FAILED,
            createdAt = "2026-08-24T10:04:00Z",
        ),
        previewMessage(
            id = "00000000-0000-0000-0000-000000000003",
            senderId = previewCurrentUserId,
            text = "Sounds good!",
            status = MessageSendStatus.SENDING,
            createdAt = "2026-08-24T10:03:00Z",
        ),
        previewMessage(
            id = "00000000-0000-0000-0000-000000000002",
            senderId = previewOtherUserId,
            text = "Are we still meeting today?",
            status = MessageSendStatus.SENT,
            createdAt = "2026-08-24T10:02:00Z",
        ),
        previewMessage(
            id = "00000000-0000-0000-0000-000000000001",
            senderId = previewCurrentUserId,
            text = "Hi! How are you?",
            status = MessageSendStatus.SENT,
            createdAt = "2026-08-24T10:01:00Z",
        ),
    ),
)

private fun previewMessage(
    id: String,
    senderId: UUID,
    text: String,
    status: MessageSendStatus,
    createdAt: String,
): Message = Message(
    id = UUID.fromString(id),
    senderId = senderId,
    textContent = text,
    createdAt = Instant.parse(createdAt),
    updatedAt = Instant.parse(createdAt),
    media = emptyList(),
    sendStatus = status,
)

@Preview(
    name = "Light",
    showBackground = true,
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Composable
private fun ChatScreenLightPreview() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        ChatScreen(
            state = previewState,
            onAction = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@Preview(
    name = "Dark",
    showBackground = true,
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ChatScreenDarkPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        ChatScreen(
            state = previewState,
            onAction = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
