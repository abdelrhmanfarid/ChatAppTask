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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
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
import com.example.chatapptask.core.ui.ChatUiTokens
import com.example.chatapptask.feature.chat.R

private const val DecodePixelSize = 720
private const val VIDEO_PLAY_GLYPH = "▶"
private const val SingleMediaAspectRatio = 4f / 3f

@Composable
fun MessageMediaContent(
    items: List<MessageMediaItemUi>,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    if (items.size == 1) {
        MessageMediaAttachment(
            item = items.first(),
            shape = RoundedCornerShape(ChatUiTokens.MediaCornerRadius),
            modifier = modifier
                .fillMaxWidth()
                .heightIn(
                    min = ChatUiTokens.SingleMediaMinHeight,
                    max = ChatUiTokens.SingleMediaMaxHeight,
                )
                .aspectRatio(SingleMediaAspectRatio),
        )
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ChatUiTokens.MediaGridGap),
    ) {
        items.chunked(2).forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ChatUiTokens.MediaGridGap),
            ) {
                rowItems.forEachIndexed { columnIndex, item ->
                    val cellIndex = rowIndex * 2 + columnIndex
                    MessageMediaAttachment(
                        item = item,
                        shape = mediaGridCellShape(
                            index = cellIndex,
                            totalCount = items.size,
                        ),
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
    shape: Shape = RoundedCornerShape(ChatUiTokens.MediaCornerRadius),
) {
    val imageDescription = stringResource(R.string.chat_message_image)
    val displayUri = item.displayUri
    if (displayUri.isNullOrBlank()) {
        MediaUnavailable(
            isVideo = false,
            shape = shape,
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
        modifier = modifier.clip(shape),
        contentScale = ContentScale.Crop,
        loading = {
            MediaLoading(
                shape = shape,
                modifier = Modifier.fillMaxSize(),
            )
        },
        error = {
            MediaUnavailable(
                isVideo = false,
                shape = shape,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

@Composable
fun MessageVideoAttachment(
    item: MessageMediaItemUi,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(ChatUiTokens.MediaCornerRadius),
) {
    val videoDescription = stringResource(R.string.chat_message_video)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .semantics { contentDescription = videoDescription },
        contentAlignment = Alignment.Center,
    ) {
        MessageVideoOverlay(
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun MessageMediaAttachment(
    item: MessageMediaItemUi,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    when (item.mediaType) {
        MediaType.IMAGE -> MessageImageAttachment(item = item, modifier = modifier, shape = shape)
        MediaType.VIDEO -> MessageVideoAttachment(item = item, modifier = modifier, shape = shape)
    }
}

@Composable
internal fun MessageVideoOverlay(
    modifier: Modifier = Modifier,
    showBadge: Boolean = true,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.58f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = VIDEO_PLAY_GLYPH,
                modifier = Modifier.clearAndSetSemantics { },
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        if (showBadge) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(ChatUiTokens.SpaceSm),
                shape = RoundedCornerShape(ChatUiTokens.MediaInnerCornerRadius),
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.68f),
            ) {
                Text(
                    text = stringResource(R.string.chat_attachment_video_badge),
                    modifier = Modifier
                        .padding(horizontal = ChatUiTokens.SpaceSm, vertical = ChatUiTokens.SpaceXs)
                        .clearAndSetSemantics { },
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun MediaLoading(
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val loadingDescription = stringResource(R.string.chat_message_image_loading)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .semantics { contentDescription = loadingDescription },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun MediaUnavailable(
    isVideo: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val description = if (isVideo) {
        stringResource(R.string.chat_message_video)
    } else {
        stringResource(R.string.chat_message_image_unavailable)
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (isVideo) {
            MessageVideoOverlay(showBadge = true)
        } else {
            Text(
                text = stringResource(R.string.chat_message_image_unavailable),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

internal fun mediaGridCellShape(
    index: Int,
    totalCount: Int,
): RoundedCornerShape {
    val outer = ChatUiTokens.MediaCornerRadius
    val inner = ChatUiTokens.MediaInnerCornerRadius
    val row = index / 2
    val col = index % 2
    val lastRow = (totalCount - 1) / 2
    val isFirstRow = row == 0
    val isLastRow = row == lastRow
    val isLeftColumn = col == 0
    val isRightColumn = col == 1
    val isSingleInLastRow = isLastRow && totalCount % 2 == 1 && index == totalCount - 1

    val topStart = when {
        isFirstRow && isLeftColumn -> outer
        else -> inner
    }
    val topEnd = when {
        isFirstRow && (isRightColumn || totalCount == 1) -> outer
        isFirstRow && isSingleInLastRow -> outer
        else -> inner
    }
    val bottomEnd = when {
        isLastRow && (isRightColumn || isSingleInLastRow) -> outer
        else -> inner
    }
    val bottomStart = when {
        isLastRow && isLeftColumn -> outer
        else -> inner
    }

    return RoundedCornerShape(
        topStart = topStart,
        topEnd = topEnd,
        bottomEnd = bottomEnd,
        bottomStart = bottomStart,
    )
}
