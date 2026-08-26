package com.example.chatapptask.fcm

/**
 * Pure decisions for incoming chat FCM notifications (no Android framework types).
 */
object ChatIncomingNotificationPolicy {
    /** Distinct from WorkManager send keys (`send-text-message:` / `send-media-message:`). */
    private const val NOTIFICATION_ID_PREFIX = "incoming-chat-message:"

    /** Distinct from child IDs and WorkManager send notification IDs. */
    private const val GROUP_SUMMARY_ID_KEY = "incoming-chat-group-summary"

    const val GROUP_KEY = "chat_messages"

    /**
     * Fixed notification ID for the single expandable group summary.
     * Separate from every [notificationId] child and from `message_send_work` IDs.
     */
    val GROUP_SUMMARY_NOTIFICATION_ID: Int = GROUP_SUMMARY_ID_KEY.hashCode()

    /**
     * Identity used when posting a grouped incoming notification (child or summary).
     */
    data class GroupedNotificationIdentity(
        val id: Int,
        val groupKey: String,
        val isGroupSummary: Boolean,
    )

    fun shouldSuppressForSender(
        payloadSenderId: String,
        currentUserId: String,
    ): Boolean = payloadSenderId.trim() == currentUserId.trim()

    fun shouldSuppressForActiveChat(isChatScreenVisible: Boolean): Boolean = isChatScreenVisible

    /**
     * Deterministic notification ID from [messageId] so duplicate FCM deliveries replace
     * the same notification instead of stacking duplicates.
     */
    fun notificationId(messageId: String): Int =
        (NOTIFICATION_ID_PREFIX + messageId.trim()).hashCode()

    fun childNotificationIdentity(messageId: String): GroupedNotificationIdentity =
        GroupedNotificationIdentity(
            id = notificationId(messageId),
            groupKey = GROUP_KEY,
            isGroupSummary = false,
        )

    fun summaryNotificationIdentity(): GroupedNotificationIdentity =
        GroupedNotificationIdentity(
            id = GROUP_SUMMARY_NOTIFICATION_ID,
            groupKey = GROUP_KEY,
            isGroupSummary = true,
        )

    /**
     * Counts active group children from (groupKey, isGroupSummary) pairs.
     * Used with [android.app.NotificationManager.getActiveNotifications] so the
     * summary reflects currently visible children without persisted state.
     */
    fun countActiveGroupChildren(
        notifications: List<Pair<String?, Boolean>>,
        groupKey: String = GROUP_KEY,
    ): Int = notifications.count { (key, isSummary) ->
        key == groupKey && !isSummary
    }

    /** Summary body count is at least 1 after a child was just posted. */
    fun groupSummaryMessageCount(activeChildCount: Int): Int =
        activeChildCount.coerceAtLeast(1)

    /**
     * Privacy-safe notification body from validated preview fields.
     * Media with meaningful caption text prefers the text preview.
     */
    fun notificationBody(
        previewKind: ChatMessageFcmPayload.PreviewKind,
        previewText: String,
        fallbackNewMessage: String = "New message",
        imageOnly: String = "Sent a photo",
        videoOnly: String = "Sent a video",
        mediaOnly: String = "Sent attachments",
    ): String {
        val text = previewText.trim()
        val hasMeaningfulText = text.isNotEmpty()
        return when (previewKind) {
            ChatMessageFcmPayload.PreviewKind.TEXT ->
                if (hasMeaningfulText) text else fallbackNewMessage
            ChatMessageFcmPayload.PreviewKind.IMAGE ->
                if (hasMeaningfulText) text else imageOnly
            ChatMessageFcmPayload.PreviewKind.VIDEO ->
                if (hasMeaningfulText) text else videoOnly
            ChatMessageFcmPayload.PreviewKind.MEDIA ->
                if (hasMeaningfulText) text else mediaOnly
        }
    }

    fun notificationTitle(senderUsername: String, fallback: String = "Chat"): String {
        val trimmed = senderUsername.trim()
        return trimmed.ifEmpty { fallback }
    }
}
