package com.example.chatapptask.feature.chat.presentation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.chatapptask.core.domain.model.MediaType
import com.example.chatapptask.core.ui.ChatUiTokens
import com.example.chatapptask.core.ui.rememberHapticAction
import com.example.chatapptask.feature.chat.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PreviewShape = RoundedCornerShape(ChatUiTokens.ComposerPreviewCorner)

@Composable
fun ComposerAttachmentPreviewRow(
    attachments: List<ComposerAttachment>,
    onRemove: (uri: String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    AnimatedVisibility(
        visible = attachments.isNotEmpty(),
        modifier = modifier,
        enter = expandVertically(animationSpec = tween(durationMillis = 180)) +
            fadeIn(animationSpec = tween(durationMillis = 180)),
        exit = shrinkVertically(animationSpec = tween(durationMillis = 140)) +
            fadeOut(animationSpec = tween(durationMillis = 140)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = ChatUiTokens.ComposerHorizontalPadding,
                    end = ChatUiTokens.ComposerHorizontalPadding,
                    top = ChatUiTokens.SpaceSm,
                ),
            verticalArrangement = Arrangement.spacedBy(ChatUiTokens.SpaceSm),
        ) {
            Text(
                text = stringResource(
                    R.string.chat_attachment_count,
                    attachments.size,
                    MAX_COMPOSER_ATTACHMENTS,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(ChatUiTokens.ComposerPreviewGap),
                contentPadding = PaddingValues(bottom = ChatUiTokens.SpaceXs),
            ) {
                items(
                    items = attachments,
                    key = { attachment -> attachment.uri },
                ) { attachment ->
                    ComposerAttachmentThumbnail(
                        attachment = attachment,
                        onRemove = rememberHapticAction { onRemove(attachment.uri) },
                        enabled = enabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComposerAttachmentThumbnail(
    attachment: ComposerAttachment,
    onRemove: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val removeDescription = stringResource(R.string.chat_attachment_remove)
    val itemDescription = when (attachment.mediaType) {
        MediaType.IMAGE -> stringResource(R.string.chat_attachment_image)
        MediaType.VIDEO -> stringResource(R.string.chat_attachment_video)
    }
    val previewBitmap = rememberAttachmentPreviewBitmap(attachment)

    Box(
        modifier = modifier
            .size(ChatUiTokens.ComposerPreviewSize)
            .semantics { contentDescription = itemDescription },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clip(PreviewShape)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = PreviewShape,
                ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
        ) {
            if (previewBitmap != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        bitmap = previewBitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    if (attachment.mediaType == MediaType.VIDEO) {
                        MessageVideoOverlay(
                            modifier = Modifier.fillMaxSize(),
                            showBadge = true,
                        )
                    }
                }
            } else {
                AttachmentPlaceholder(mediaType = attachment.mediaType)
            }
        }
        IconButton(
            onClick = onRemove,
            enabled = enabled,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(ChatUiTokens.ComposerActionSize)
                .semantics { contentDescription = removeDescription },
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.62f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "×",
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun AttachmentPlaceholder(mediaType: MediaType) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when (mediaType) {
                MediaType.IMAGE -> stringResource(R.string.chat_attachment_image_placeholder)
                MediaType.VIDEO -> stringResource(R.string.chat_attachment_video_placeholder)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun rememberAttachmentPreviewBitmap(attachment: ComposerAttachment): ImageBitmap? {
    val context = LocalContext.current
    val preview by produceState<ImageBitmap?>(
        initialValue = null,
        attachment.uri,
        attachment.mediaType,
        context,
    ) {
        value = null
        val uri = runCatching { Uri.parse(attachment.uri) }.getOrNull() ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                when (attachment.mediaType) {
                    MediaType.IMAGE -> decodeImagePreview(context.contentResolver, uri)
                    MediaType.VIDEO -> decodeVideoPreview(context, uri)
                }
            }.getOrNull()
        }
    }
    return preview
}

private fun decodeImagePreview(
    contentResolver: android.content.ContentResolver,
    uri: Uri,
): ImageBitmap? =
    contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input)?.asImageBitmap()
    }

private fun decodeVideoPreview(
    context: android.content.Context,
    uri: Uri,
): ImageBitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val frame: Bitmap? = retriever.getFrameAtTime(0)
        frame?.asImageBitmap()
    } finally {
        runCatching { retriever.release() }
    }
}
