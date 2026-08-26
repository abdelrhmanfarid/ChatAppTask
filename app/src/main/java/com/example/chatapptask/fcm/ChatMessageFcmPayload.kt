package com.example.chatapptask.fcm

/**
 * Validated data-only FCM chat payload (schema_version = "1").
 * Used only for notification presentation and navigation extras — never Room writes.
 */
data class ChatMessageFcmPayload(
    val messageId: String,
    val senderId: String,
    val senderUsername: String,
    val previewKind: PreviewKind,
    val previewText: String,
) {
    enum class PreviewKind {
        TEXT,
        IMAGE,
        VIDEO,
        MEDIA,
    }

    companion object {
        const val TYPE_CHAT_MESSAGE = "chat_message"
        const val SCHEMA_VERSION_1 = "1"

        const val KEY_TYPE = "type"
        const val KEY_SCHEMA_VERSION = "schema_version"
        const val KEY_MESSAGE_ID = "message_id"
        const val KEY_SENDER_ID = "sender_id"
        const val KEY_SENDER_USERNAME = "sender_username"
        const val KEY_PREVIEW_KIND = "preview_kind"
        const val KEY_PREVIEW_TEXT = "preview_text"

        /**
         * Parses and validates a data-only FCM map.
         * Returns null when the payload is not an accepted chat_message v1 payload.
         */
        fun parse(data: Map<String, String>): ChatMessageFcmPayload? {
            if (data[KEY_TYPE]?.trim() != TYPE_CHAT_MESSAGE) return null
            if (data[KEY_SCHEMA_VERSION]?.trim() != SCHEMA_VERSION_1) return null

            val messageId = data[KEY_MESSAGE_ID]?.trim().orEmpty()
            val senderId = data[KEY_SENDER_ID]?.trim().orEmpty()
            if (messageId.isEmpty() || senderId.isEmpty()) return null

            val previewKind = parsePreviewKind(data[KEY_PREVIEW_KIND]) ?: return null
            val senderUsername = data[KEY_SENDER_USERNAME]?.trim().orEmpty()
            val previewText = data[KEY_PREVIEW_TEXT]?.trim().orEmpty()

            return ChatMessageFcmPayload(
                messageId = messageId,
                senderId = senderId,
                senderUsername = senderUsername,
                previewKind = previewKind,
                previewText = previewText,
            )
        }

        private fun parsePreviewKind(raw: String?): PreviewKind? =
            when (raw?.trim()?.lowercase()) {
                "text" -> PreviewKind.TEXT
                "image" -> PreviewKind.IMAGE
                "video" -> PreviewKind.VIDEO
                "media" -> PreviewKind.MEDIA
                else -> null
            }
    }
}
