package com.example.chatapptask.fcm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.chatapptask.MainActivity

/**
 * Intent extras for opening Chat from an incoming-message notification.
 * Startup/profile resolution remains authoritative; this only launches [MainActivity]
 * and carries [EXTRA_MESSAGE_ID] for future exact-message navigation.
 */
object ChatNotificationIntents {
    const val ACTION_OPEN_CHAT = "com.example.chatapptask.action.OPEN_CHAT"
    const val EXTRA_MESSAGE_ID = "chat_notification_message_id"
    private const val EXTRA_HANDLED = "chat_notification_handled"

    data class OpenChatTarget(
        val action: String = ACTION_OPEN_CHAT,
        val messageId: String,
    )

    fun openChatTarget(messageId: String): OpenChatTarget =
        OpenChatTarget(messageId = messageId.trim())

    fun openChatIntent(context: Context, messageId: String): Intent {
        val target = openChatTarget(messageId)
        return Intent(context, MainActivity::class.java).apply {
            action = target.action
            putExtra(EXTRA_MESSAGE_ID, target.messageId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }

    fun contentPendingIntent(context: Context, messageId: String): PendingIntent {
        val intent = openChatIntent(context, messageId)
        return PendingIntent.getActivity(
            context,
            ChatIncomingNotificationPolicy.notificationId(messageId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Summary tap opens Chat the same way as a child (no scroll-to-message).
     * Uses the fixed summary notification ID as the PendingIntent request code.
     */
    fun groupSummaryContentPendingIntent(context: Context, latestMessageId: String): PendingIntent {
        val intent = openChatIntent(context, latestMessageId)
        return PendingIntent.getActivity(
            context,
            ChatIncomingNotificationPolicy.GROUP_SUMMARY_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Pure consume decision used by [consumeOpenChatMessageId].
     * Returns the message id when this delivery should be handled once.
     */
    fun extractOpenChatMessageId(
        action: String?,
        messageIdExtra: String?,
        alreadyHandled: Boolean,
    ): String? {
        if (action != ACTION_OPEN_CHAT || alreadyHandled) return null
        return messageIdExtra?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Returns the carried message_id once, then marks the intent consumed so
     * onCreate + onNewIntent (or recreation) do not re-process the same delivery.
     */
    fun consumeOpenChatMessageId(intent: Intent?): String? {
        if (intent == null) return null
        val messageId = extractOpenChatMessageId(
            action = intent.action,
            messageIdExtra = intent.getStringExtra(EXTRA_MESSAGE_ID),
            alreadyHandled = intent.getBooleanExtra(EXTRA_HANDLED, false),
        ) ?: return null
        intent.putExtra(EXTRA_HANDLED, true)
        return messageId
    }

    fun isOpenChatAction(intent: Intent?): Boolean =
        intent?.action == ACTION_OPEN_CHAT
}
