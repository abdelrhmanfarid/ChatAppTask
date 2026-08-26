package com.example.chatapptask.feature.chat.presentation

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
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
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Dimension
import coil3.size.Size
import com.example.chatapptask.core.domain.model.MediaType
import com.example.chatapptask.core.domain.model.MediaUploadStatus
import com.example.chatapptask.core.domain.model.Message
import com.example.chatapptask.core.domain.model.MessageMedia
import com.example.chatapptask.core.domain.model.MessageSendStatus
import com.example.chatapptask.core.domain.model.User
import com.example.chatapptask.core.ui.ChatUiTokens
import com.example.chatapptask.core.ui.clearFocusOnTap
import com.example.chatapptask.core.ui.rememberHapticAction
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
    var snackbarIsError by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val unsupportedMediaMessage = stringResource(R.string.chat_attachment_unsupported)
    val oversizedMediaMessage = stringResource(R.string.chat_attachment_too_large)

    fun handlePickedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val resolved = resolveComposerAttachments(uris, context.contentResolver)
        if (resolved.attachments.isNotEmpty()) {
            viewModel.onAction(ChatAction.MediaSelected(resolved.attachments))
        }
        val snackbarMessage = when {
            resolved.skippedOversizedCount > 0 -> oversizedMediaMessage
            resolved.skippedUnsupportedCount > 0 -> unsupportedMediaMessage
            else -> null
        }
        if (snackbarMessage != null) {
            scope.launch {
                snackbarIsError = false
                snackbarHostState.showSnackbar(snackbarMessage)
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
                    is ChatEvent.ShowError -> {
                        snackbarIsError = event.isError
                        snackbarHostState.showSnackbar(context.getString(event.messageRes))
                    }
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
        snackbarIsError = snackbarIsError,
        chatMediaPublicUrl = viewModel::publicChatMediaUrl,
        profileImagePublicUrl = viewModel::publicProfileImageUrl,
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
    snackbarIsError: Boolean = false,
    chatMediaPublicUrl: (String) -> String? = { null },
    profileImagePublicUrl: (String?) -> String? = { null },
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.chat_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
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
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                if (snackbarIsError) {
                    Snackbar(
                        snackbarData = data,
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        actionContentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    Snackbar(snackbarData = data)
                }
            }
        },
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
            val isNearNewest = listState.firstVisibleItemIndex <= NEAR_NEWEST_ITEM_INDEX
            val shouldScroll = shouldScrollToOutgoingOptimisticMessage(
                previousNewestMessageId = previousNewestMessageId,
                newestMessage = newestMessage,
                currentUserId = state.currentUserId,
            ) || shouldScrollToIncomingLiveMessage(
                previousNewestMessageId = previousNewestMessageId,
                newestMessage = newestMessage,
                isNearNewest = isNearNewest,
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
            when {
                !state.hasResolvedLocalMessages -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                state.messages.isEmpty() -> {
                    EmptyChatState(modifier = Modifier.fillMaxSize())
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        reverseLayout = true,
                        contentPadding = PaddingValues(
                            horizontal = ChatUiTokens.MessageListHorizontalPadding,
                            vertical = ChatUiTokens.MessageListVerticalPadding,
                        ),
                        verticalArrangement = Arrangement.spacedBy(ChatUiTokens.MessageRowSpacing),
                    ) {
                        items(
                            items = state.messages,
                            key = { message -> message.id },
                        ) { message ->
                            val sender = state.sendersById[message.senderId]
                            MessageBubble(
                                message = message,
                                isOutgoing = state.currentUserId == message.senderId,
                                senderUsername = sender?.username,
                                senderAvatarUrl = profileImagePublicUrl(sender?.profileImagePath),
                                onRetry = { onAction(ChatAction.RetryMessage(message.id)) },
                                chatMediaPublicUrl = chatMediaPublicUrl,
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
}

@Composable
fun MessageBubble(
    message: Message,
    isOutgoing: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    senderUsername: String? = null,
    senderAvatarUrl: String? = null,
    chatMediaPublicUrl: (String) -> String? = { null },
) {
    val bubbleColor = if (isOutgoing) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val metaColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val bubbleShape = if (isOutgoing) {
        ChatUiTokens.OutgoingBubbleShape
    } else {
        ChatUiTokens.IncomingBubbleShape
    }
    val mediaItems = remember(message.id, message.media, message.sendStatus, chatMediaPublicUrl) {
        messageMediaItemsForDisplay(message, chatMediaPublicUrl)
    }
    val text = message.textContent?.takeIf(String::isNotBlank)
    val displayName = senderUsername?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.chat_sender_unknown)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isOutgoing) {
            MessageSenderAvatar(
                avatarUrl = senderAvatarUrl,
                modifier = Modifier.padding(end = ChatUiTokens.MessageAvatarGap),
            )
        }
        Column(
            horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = ChatUiTokens.MessageBubbleMaxWidth),
        ) {
            if (!isOutgoing) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        bottom = ChatUiTokens.SpaceXs,
                        start = ChatUiTokens.SpaceXs,
                        end = ChatUiTokens.SpaceXs,
                    ),
                )
            }
            Surface(
                shape = bubbleShape,
                color = bubbleColor,
                contentColor = contentColor,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(
                        vertical = ChatUiTokens.MessageBubblePaddingVertical,
                    ),
                ) {
                    if (mediaItems.isNotEmpty()) {
                        MessageMediaContent(
                            items = mediaItems,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ChatUiTokens.SpaceXs),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .padding(horizontal = ChatUiTokens.MessageBubblePaddingHorizontal)
                            .padding(
                                top = when {
                                    mediaItems.isNotEmpty() && text != null -> ChatUiTokens.SpaceSm
                                    else -> 0.dp
                                },
                            ),
                    ) {
                        if (text != null) {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        Row(
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = ChatUiTokens.SpaceXs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ChatUiTokens.SpaceXs + 2.dp),
                        ) {
                            Text(
                                text = message.createdAt.toDisplayTime(),
                                style = MaterialTheme.typography.labelSmall,
                                color = metaColor,
                            )
                            if (isOutgoing) {
                                MessageSendState(
                                    status = message.sendStatus,
                                    metaColor = metaColor,
                                    onRetry = onRetry,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (isOutgoing) {
            MessageSenderAvatar(
                avatarUrl = senderAvatarUrl,
                modifier = Modifier.padding(start = ChatUiTokens.MessageAvatarGap),
            )
        }
    }
}

@Composable
private fun MessageSenderAvatar(
    avatarUrl: String?,
    modifier: Modifier = Modifier,
) {
    val avatarDescription = stringResource(R.string.chat_sender_avatar)
    val placeholderColor = MaterialTheme.colorScheme.secondaryContainer
    val placeholderContentColor = MaterialTheme.colorScheme.onSecondaryContainer

    if (avatarUrl.isNullOrBlank()) {
        Box(
            modifier = modifier
                .size(ChatUiTokens.MessageAvatarSize)
                .clip(CircleShape)
                .background(placeholderColor)
                .semantics { contentDescription = avatarDescription },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = SENDER_AVATAR_PLACEHOLDER_GLYPH,
                style = MaterialTheme.typography.labelLarge,
                color = placeholderContentColor,
            )
        }
        return
    }

    val context = LocalContext.current
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(avatarUrl)
            .size(Size(Dimension.Pixels(SenderAvatarDecodeSize), Dimension.Pixels(SenderAvatarDecodeSize)))
            .crossfade(true)
            .build(),
        contentDescription = avatarDescription,
        modifier = modifier
            .size(ChatUiTokens.MessageAvatarSize)
            .clip(CircleShape),
        contentScale = ContentScale.Crop,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(placeholderColor),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp,
                    color = placeholderContentColor,
                )
            }
        },
        error = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(placeholderColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = SENDER_AVATAR_PLACEHOLDER_GLYPH,
                    style = MaterialTheme.typography.labelLarge,
                    color = placeholderContentColor,
                )
            }
        },
    )
}

@Composable
private fun MessageSendState(
    status: MessageSendStatus,
    metaColor: Color,
    onRetry: () -> Unit,
) {
    when (status) {
        MessageSendStatus.SENDING -> {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = metaColor,
            )
            Text(
                text = stringResource(R.string.chat_sending),
                style = MaterialTheme.typography.labelSmall,
                color = metaColor,
            )
        }

        MessageSendStatus.SENT -> Text(
            text = stringResource(R.string.chat_sent),
            style = MaterialTheme.typography.labelSmall,
            color = metaColor,
        )

        MessageSendStatus.FAILED -> {
            val hapticRetry = rememberHapticAction(onRetry)
            Text(
                text = stringResource(R.string.chat_failed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(
                onClick = hapticRetry,
                contentPadding = PaddingValues(
                    horizontal = ChatUiTokens.SpaceSm,
                    vertical = ChatUiTokens.SpaceXs,
                ),
            ) {
                Text(
                    text = stringResource(R.string.chat_retry),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
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
    val hapticSend = rememberHapticAction(onSend)
    val composerShape = RoundedCornerShape(
        topStart = ChatUiTokens.SpaceMd,
        topEnd = ChatUiTokens.SpaceMd,
    )
    val sendContainerColor by animateColorAsState(
        targetValue = if (canSend) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = tween(durationMillis = 160),
        label = "sendContainerColor",
    )
    val sendContentColor by animateColorAsState(
        targetValue = if (canSend) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 160),
        label = "sendContentColor",
    )
    val sendScale by animateFloatAsState(
        targetValue = if (canSend) 1f else 0.96f,
        animationSpec = tween(durationMillis = 160),
        label = "sendScale",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = composerShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ComposerAttachmentPreviewRow(
                attachments = selectedAttachments,
                onRemove = onRemoveAttachment,
                enabled = !isSending,
            )
            Row(
                modifier = Modifier.padding(
                    horizontal = ChatUiTokens.ComposerHorizontalPadding,
                    vertical = ChatUiTokens.ComposerVerticalPadding,
                ),
                verticalAlignment = Alignment.Bottom,
            ) {
                FilledTonalIconButton(
                    onClick = onAttachmentClick,
                    enabled = !isSending,
                    modifier = Modifier
                        .size(ChatUiTokens.ComposerActionSize)
                        .semantics { contentDescription = attachmentDescription },
                    shape = MaterialTheme.shapes.medium,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.width(ChatUiTokens.SpaceSm))
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.chat_composer_placeholder),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    minLines = 1,
                    maxLines = 4,
                    shape = MaterialTheme.shapes.large,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { if (canSend) hapticSend() },
                    ),
                )
                Spacer(Modifier.width(ChatUiTokens.SpaceSm))
                Button(
                    onClick = hapticSend,
                    enabled = canSend,
                    modifier = Modifier
                        .defaultMinSize(minWidth = ChatUiTokens.ComposerSendMinWidth)
                        .height(ChatUiTokens.ComposerActionSize)
                        .graphicsLayer {
                            scaleX = sendScale
                            scaleY = sendScale
                        },
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = sendContainerColor,
                        contentColor = sendContentColor,
                        disabledContainerColor = sendContainerColor,
                        disabledContentColor = sendContentColor,
                    ),
                    contentPadding = PaddingValues(horizontal = ChatUiTokens.SpaceMd),
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = sendContentColor,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.chat_send),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
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
        modifier = modifier.padding(ChatUiTokens.SpaceXxl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ChatUiTokens.SpaceSm),
        ) {
            Text(
                text = stringResource(R.string.chat_empty_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.chat_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val OLDER_LOADING_ITEM_KEY = "older-messages-loading"
private const val NEAR_NEWEST_ITEM_INDEX = 1
private const val SenderAvatarDecodeSize = 96
private const val SENDER_AVATAR_PLACEHOLDER_GLYPH = "?"

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

internal fun shouldScrollToIncomingLiveMessage(
    previousNewestMessageId: UUID?,
    newestMessage: Message?,
    isNearNewest: Boolean,
): Boolean {
    if (!isNearNewest) return false
    if (newestMessage == null || previousNewestMessageId == null) return false
    return newestMessage.id != previousNewestMessageId
}

private fun Instant.toDisplayTime(): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .format(this)

private val previewCurrentUserId = UUID.fromString("33eed91f-846c-49c8-851d-bca519b01432")
private val previewOtherUserId = UUID.fromString("44eed91f-846c-49c8-851d-bca519b01432")

private val previewState = ChatUiState(
    currentUserId = previewCurrentUserId,
    hasResolvedLocalMessages = true,
    sendersById = mapOf(
        previewCurrentUserId to previewUser(
            id = previewCurrentUserId,
            username = "You",
        ),
        previewOtherUserId to previewUser(
            id = previewOtherUserId,
            username = "Alex",
        ),
    ),
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
            id = "00000000-0000-0000-0000-000000000006",
            senderId = previewCurrentUserId,
            text = "Photo from the walk.",
            status = MessageSendStatus.SENT,
            createdAt = "2026-08-24T10:06:00Z",
            media = listOf(
                previewMedia(
                    id = "00000000-0000-0000-0000-000000000016",
                    messageId = "00000000-0000-0000-0000-000000000006",
                    storagePath = "preview/image.jpg",
                    mediaType = MediaType.IMAGE,
                    position = 0,
                ),
            ),
        ),
        previewMessage(
            id = "00000000-0000-0000-0000-000000000005",
            senderId = previewOtherUserId,
            text = null,
            status = MessageSendStatus.SENT,
            createdAt = "2026-08-24T10:05:00Z",
            media = listOf(
                previewMedia(
                    id = "00000000-0000-0000-0000-000000000015",
                    messageId = "00000000-0000-0000-0000-000000000005",
                    storagePath = "preview/video.mp4",
                    mediaType = MediaType.VIDEO,
                    mimeType = "video/mp4",
                    position = 0,
                ),
            ),
        ),
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
    text: String?,
    status: MessageSendStatus,
    createdAt: String,
    media: List<MessageMedia> = emptyList(),
): Message = Message(
    id = UUID.fromString(id),
    senderId = senderId,
    textContent = text,
    createdAt = Instant.parse(createdAt),
    updatedAt = Instant.parse(createdAt),
    media = media,
    sendStatus = status,
)

private fun previewUser(
    id: UUID,
    username: String,
): User = User(
    id = id,
    username = username,
    profileImagePath = null,
    age = null,
    createdAt = Instant.parse("2026-08-24T09:00:00Z"),
    updatedAt = Instant.parse("2026-08-24T09:00:00Z"),
)

private fun previewMedia(
    id: String,
    messageId: String,
    storagePath: String,
    mediaType: MediaType,
    position: Int,
    mimeType: String = "image/jpeg",
): MessageMedia = MessageMedia(
    id = UUID.fromString(id),
    messageId = UUID.fromString(messageId),
    storagePath = storagePath,
    mediaType = mediaType,
    mimeType = mimeType,
    position = position,
    sizeBytes = null,
    width = null,
    height = null,
    localUri = null,
    uploadStatus = MediaUploadStatus.UPLOADED,
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
