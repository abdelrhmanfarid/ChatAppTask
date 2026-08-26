package com.example.chatapptask.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Modest shared presentation values for chat and profile UI.
 * Prefer [androidx.compose.material3.MaterialTheme] for color, type, and shapes.
 */
object ChatUiTokens {
    val SpaceXs: Dp = 4.dp
    val SpaceSm: Dp = 8.dp
    val SpaceMd: Dp = 12.dp
    val SpaceLg: Dp = 16.dp
    val SpaceXl: Dp = 24.dp
    val SpaceXxl: Dp = 32.dp

    val ScreenHorizontalPadding: Dp = SpaceXl
    val ScreenVerticalPadding: Dp = SpaceXxl
    val FormFieldSpacing: Dp = SpaceMd
    val SectionSpacing: Dp = SpaceXl

    val ProfileAvatarSize: Dp = 120.dp
    val ProfileAvatarBadgeSize: Dp = 36.dp
    val ProfilePrimaryActionHeight: Dp = 52.dp
    val ProfileContentMaxWidth: Dp = 480.dp

    val MessageListHorizontalPadding: Dp = SpaceLg
    val MessageListVerticalPadding: Dp = SpaceMd
    val MessageRowSpacing: Dp = 10.dp
    val MessageBubbleMaxWidth: Dp = 300.dp
    val MessageBubblePaddingHorizontal: Dp = 14.dp
    val MessageBubblePaddingVertical: Dp = 10.dp
    val MessageAvatarSize: Dp = 36.dp
    val MessageAvatarGap: Dp = SpaceSm

    private val BubbleCorner: Dp = 18.dp
    private val BubbleTailCorner: Dp = 4.dp

    val IncomingBubbleShape = RoundedCornerShape(
        topStart = BubbleCorner,
        topEnd = BubbleCorner,
        bottomEnd = BubbleCorner,
        bottomStart = BubbleTailCorner,
    )

    val OutgoingBubbleShape = RoundedCornerShape(
        topStart = BubbleCorner,
        topEnd = BubbleCorner,
        bottomEnd = BubbleTailCorner,
        bottomStart = BubbleCorner,
    )

    val MediaCornerRadius: Dp = 12.dp
    val MediaInnerCornerRadius: Dp = 4.dp
    val MediaGridGap: Dp = SpaceXs
    val SingleMediaMaxHeight: Dp = 208.dp
    val SingleMediaMinHeight: Dp = 140.dp

    val ComposerPreviewSize: Dp = 76.dp
    val ComposerPreviewCorner: Dp = 10.dp
    val ComposerPreviewGap: Dp = SpaceSm
    val ComposerHorizontalPadding: Dp = SpaceMd
    val ComposerVerticalPadding: Dp = SpaceSm
    val ComposerActionSize: Dp = 48.dp
    val ComposerSendMinWidth: Dp = 72.dp
}
