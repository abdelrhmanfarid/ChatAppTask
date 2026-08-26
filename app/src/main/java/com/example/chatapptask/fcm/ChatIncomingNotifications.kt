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
            .setGroup(ChatIncomingNotificationPolicy.GROUP_KEY)
            .setContentIntent(
                ChatNotificationIntents.contentPendingIntent(context, payload.messageId),
            )
            .build()

        val id = ChatIncomingNotificationPolicy.notificationId(payload.messageId)
        manager.notify(id, notification)
        return true
    }
}
