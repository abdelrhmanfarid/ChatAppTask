package com.example.chatapptask.feature.chat.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Dimension
import coil3.size.Size
import com.example.chatapptask.core.domain.model.MediaType
import com.example.chatapptask.feature.chat.R

private val MediaCornerShape = RoundedCornerShape(12.dp)
private val SinglePreviewHeight = 192.dp
private const val DecodePixelSize = 720
private const val VIDEO_PLAY_GLYPH = "▶"

@Composable
fun MessageMediaContent(
    items: List<MessageMediaItemUi>,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    if (items.size == 1) {
        MessageMediaAttachment(
            item = items.first(),
            modifier = modifier
                .fillMaxWidth()
                .height(SinglePreviewHeight),
        )
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                rowItems.forEach { item ->
                    MessageMediaAttachment(
                        item = item,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun MessageImageAttachment(
    item: MessageMediaItemUi,
    modifier: Modifier = Modifier,
) {
    val imageDescription = stringResource(R.string.chat_message_image)
    val displayUri = item.displayUri
    if (displayUri.isNullOrBlank()) {
        MediaUnavailable(
            isVideo = false,
            modifier = modifier,
        )
        return
    }

    val context = LocalContext.current
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(displayUri)
            .size(Size(Dimension.Pixels(DecodePixelSize), Dimension.Pixels(DecodePixelSize)))
            .crossfade(true)
            .build(),
        contentDescription = imageDescription,
        modifier = modifier.clip(MediaCornerShape),
        contentScale = ContentScale.Crop,
        loading = {
            MediaLoading(modifier = Modifier.fillMaxSize())
        },
        error = {
            MediaUnavailable(
                isVideo = false,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

@Composable
fun MessageVideoAttachment(
    item: MessageMediaItemUi,
    modifier: Modifier = Modifier,
) {
    val videoDescription = stringResource(R.string.chat_message_video)
    Box(
        modifier = modifier
            .clip(MediaCornerShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { contentDescription = videoDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = VIDEO_PLAY_GLYPH,
                modifier = Modifier.clearAndSetSemantics { },
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = stringResource(R.string.chat_attachment_video_badge),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .clearAndSetSemantics { },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MessageMediaAttachment(
    item: MessageMediaItemUi,
    modifier: Modifier = Modifier,
) {
    when (item.mediaType) {
        MediaType.IMAGE -> MessageImageAttachment(item = item, modifier = modifier)
        MediaType.VIDEO -> MessageVideoAttachment(item = item, modifier = modifier)
    }
}

@Composable
private fun MediaLoading(modifier: Modifier = Modifier) {
    val loadingDescription = stringResource(R.string.chat_message_image_loading)
    Box(
        modifier = modifier
            .clip(MediaCornerShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
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
private fun MediaUnavailable(
    isVideo: Boolean,
    modifier: Modifier = Modifier,
) {
    val description = if (isVideo) {
        stringResource(R.string.chat_message_video)
    } else {
        stringResource(R.string.chat_message_image_unavailable)
    }
    Box(
        modifier = modifier
            .clip(MediaCornerShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isVideo) {
                stringResource(R.string.chat_attachment_video_placeholder)
            } else {
                stringResource(R.string.chat_message_image_unavailable)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }
}
