package com.example.chatapptask.data.chat.worker

import android.content.Intent
import java.util.UUID

internal object MessageSendWorkActions {
    const val ACTION_RETRY = "com.example.chatapptask.action.RETRY_TEXT_SEND"
    const val ACTION_CANCEL = "com.example.chatapptask.action.CANCEL_TEXT_SEND"
    const val EXTRA_MESSAGE_ID = "message_id"

    fun messageIdFrom(intent: Intent): UUID? =
        messageIdFrom(intent.getStringExtra(EXTRA_MESSAGE_ID))

    fun messageIdFrom(rawId: String?): UUID? = rawId?.toUuidOrNull()
}

internal fun String.toUuidOrNull(): UUID? =
    try {
        UUID.fromString(this).takeIf { parsedId ->
            parsedId.toString().equals(this, ignoreCase = true)
        }
    } catch (_: IllegalArgumentException) {
        null
    }
