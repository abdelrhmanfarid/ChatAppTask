package com.example.chatapptask.fcm

/**
 * Pure decisions for incoming chat FCM notifications (no Android framework types).
 */
object ChatIncomingNotificationPolicy {
    /** Distinct from WorkManager send keys (`send-text-message:` / `send-media-message:`). */
    private const val NOTIFICATION_ID_PREFIX = "incoming-chat-message:"

    const val GROUP_KEY = "chat_messages"

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
