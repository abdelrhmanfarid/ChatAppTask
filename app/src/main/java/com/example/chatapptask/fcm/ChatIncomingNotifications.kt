package com.example.chatapptask.fcm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.chatapptask.R

/**
 * Incoming chat message notifications (distinct from WorkManager channel `message_send_work`).
 *
 * Each message posts a child notification under a stable group key, plus one fixed
 * group-summary notification so the shade can collapse them into an expandable group.
 */
object ChatIncomingNotifications {
    const val CHANNEL_ID = "chat_messages"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_chat_messages_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_chat_messages_description)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Android 13+ requires [Manifest.permission.POST_NOTIFICATIONS]; older releases
     * still respect the user notification toggle via [NotificationManager.areNotificationsEnabled].
     */
    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.areNotificationsEnabled()
    }

    fun post(context: Context, payload: ChatMessageFcmPayload): Boolean {
        if (!canPostNotifications(context)) return false
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false

        val title = ChatIncomingNotificationPolicy.notificationTitle(
            senderUsername = payload.senderUsername,
            fallback = context.getString(R.string.notification_chat_fallback_title),
        )
        val body = ChatIncomingNotificationPolicy.notificationBody(
            previewKind = payload.previewKind,
            previewText = payload.previewText,
            fallbackNewMessage = context.getString(R.string.notification_chat_body_new_message),
            imageOnly = context.getString(R.string.notification_chat_body_photo),
            videoOnly = context.getString(R.string.notification_chat_body_video),
            mediaOnly = context.getString(R.string.notification_chat_body_attachments),
        )

        val childIdentity = ChatIncomingNotificationPolicy.childNotificationIdentity(payload.messageId)
        val publicVersion = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(context.getString(R.string.notification_chat_public_title))
            .setContentText(context.getString(R.string.notification_chat_public_text))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .build()

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setGroup(childIdentity.groupKey)
            .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
            .setContentIntent(
                ChatNotificationIntents.contentPendingIntent(context, payload.messageId),
            )
            .build()

        manager.notify(childIdentity.id, notification)
        postGroupSummary(context, manager, latestMessageId = payload.messageId)
        return true
    }

    private fun postGroupSummary(
        context: Context,
        manager: NotificationManager,
        latestMessageId: String,
    ) {
        val summaryIdentity = ChatIncomingNotificationPolicy.summaryNotificationIdentity()
        val childCount = ChatIncomingNotificationPolicy.groupSummaryMessageCount(
            ChatIncomingNotificationPolicy.countActiveGroupChildren(
                manager.activeNotifications.map { sbn ->
                    val notification = sbn.notification
                    val isSummary =
                        (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
                    notification.group to isSummary
                },
            ),
        )
        val summaryText = context.resources.getQuantityString(
            R.plurals.notification_chat_group_summary_text,
            childCount,
            childCount,
        )
        val summaryTitle = context.getString(R.string.notification_chat_group_summary_title)

        val publicVersion = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(context.getString(R.string.notification_chat_public_title))
            .setContentText(context.getString(R.string.notification_chat_public_text))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .build()

        val summary = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(summaryTitle)
            .setContentText(summaryText)
            .setStyle(
                Notification.InboxStyle()
                    .setSummaryText(summaryText),
            )
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setGroup(summaryIdentity.groupKey)
            .setGroupSummary(summaryIdentity.isGroupSummary)
            .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
            .setContentIntent(
                ChatNotificationIntents.groupSummaryContentPendingIntent(
                    context,
                    latestMessageId,
                ),
            )
            .build()

        manager.notify(summaryIdentity.id, summary)
    }
}
