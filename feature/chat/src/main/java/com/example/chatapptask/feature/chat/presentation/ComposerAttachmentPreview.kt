package com.example.chatapptask.feature.chat.presentation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import com.example.chatapptask.feature.chat.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ComposerAttachmentPreviewRow(
    attachments: List<ComposerAttachment>,
    onRemove: (uri: String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (attachments.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            items(
                items = attachments,
                key = { attachment -> attachment.uri },
            ) { attachment ->
                ComposerAttachmentThumbnail(
                    attachment = attachment,
                    onRemove = { onRemove(attachment.uri) },
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
private fun ComposerAttachmentThumbnail(
    attachment: ComposerAttachment,
    onRemove: () -> Unit,
    enabled: Boolean,
) {
    val removeDescription = stringResource(R.string.chat_attachment_remove)
    val itemDescription = when (attachment.mediaType) {
        MediaType.IMAGE -> stringResource(R.string.chat_attachment_image)
        MediaType.VIDEO -> stringResource(R.string.chat_attachment_video)
    }
    val previewBitmap = rememberAttachmentPreviewBitmap(attachment)

    Box(
        modifier = Modifier
            .size(72.dp)
            .semantics { contentDescription = itemDescription },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp,
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
                        VideoBadge(modifier = Modifier.align(Alignment.BottomStart))
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
                .size(28.dp)
                .semantics { contentDescription = removeDescription },
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "×",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun VideoBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(6.dp),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f),
    ) {
        Text(
            text = stringResource(R.string.chat_attachment_video_badge),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
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
