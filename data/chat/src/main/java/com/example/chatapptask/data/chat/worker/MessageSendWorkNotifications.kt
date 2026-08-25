package com.example.chatapptask.data.chat.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import androidx.work.ForegroundInfo
import com.example.chatapptask.data.chat.R
import java.util.UUID

object MessageSendWorkNotifications {
    internal const val CHANNEL_ID = "message_send_work"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_message_send_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_message_send_description)
        }
        manager.createNotificationChannel(channel)
    }

    internal fun notificationId(workKey: String): Int = workKey.hashCode()

    internal fun actionRequestCode(workKey: String, action: String): Int =
        31 * workKey.hashCode() + action.hashCode()

    internal fun textMessageForegroundInfo(context: Context, messageId: UUID): ForegroundInfo {
        ensureChannel(context)
        val workKey = textMessageUniqueWorkName(messageId)
        val notification = build(
            context = context,
            workKey = workKey,
            messageId = messageId,
            title = context.getString(R.string.notification_sending_message),
            text = context.getString(R.string.notification_message_being_sent),
            progress = MessageSendWorkProgress.Indeterminate,
            showRetry = false,
            showCancel = true,
        )
        val id = notificationId(workKey)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                id,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(id, notification)
        }
    }

    internal fun mediaMessageForegroundInfo(context: Context, messageId: UUID): ForegroundInfo {
        ensureChannel(context)
        val workKey = mediaMessageUniqueWorkName(messageId)
        val notification = build(
            context = context,
            workKey = workKey,
            messageId = messageId,
            title = context.getString(R.string.notification_sending_message),
            text = context.getString(R.string.notification_message_being_sent),
            progress = MessageSendWorkProgress.Indeterminate,
            showRetry = false,
            showCancel = true,
        )
        val id = notificationId(workKey)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                id,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(id, notification)
        }
    }

    /**
     * Builds an ongoing send/upload notification.
     *
     * Pass [MessageSendWorkProgress.Determinate] later for media copy such as
     * "Uploading 2 of 5" without changing the channel or ID scheme.
     */
    internal fun build(
        context: Context,
        workKey: String,
        messageId: UUID,
        title: String,
        text: String?,
        progress: MessageSendWorkProgress,
        showRetry: Boolean = true,
        showCancel: Boolean = true,
    ): Notification {
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .applyProgress(progress)
        if (!text.isNullOrBlank()) {
            builder.setContentText(text)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        if (showRetry) {
            builder.addAction(
                action(
                    context = context,
                    workKey = workKey,
                    messageId = messageId,
                    action = MessageSendWorkActions.ACTION_RETRY,
                    title = context.getString(R.string.notification_action_retry),
                ),
            )
        }
        if (showCancel) {
            builder.addAction(
                action(
                    context = context,
                    workKey = workKey,
                    messageId = messageId,
                    action = MessageSendWorkActions.ACTION_CANCEL,
                    title = context.getString(R.string.notification_action_cancel),
                ),
            )
        }
        return builder.build()
    }

    private fun action(
        context: Context,
        workKey: String,
        messageId: UUID,
        action: String,
        title: String,
    ): Notification.Action {
        val intent = Intent(context, MessageSendWorkActionReceiver::class.java).apply {
            this.action = action
            putExtra(MessageSendWorkActions.EXTRA_MESSAGE_ID, messageId.toString())
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            actionRequestCode(workKey, action),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(
            Icon.createWithResource(context, android.R.drawable.stat_sys_upload),
            title,
            pendingIntent,
        ).build()
    }
}

internal sealed class MessageSendWorkProgress {
    data object Indeterminate : MessageSendWorkProgress()

    data class Determinate(
        val current: Int,
        val total: Int,
    ) : MessageSendWorkProgress()
}

private fun Notification.Builder.applyProgress(
    progress: MessageSendWorkProgress,
): Notification.Builder = when (progress) {
    MessageSendWorkProgress.Indeterminate -> setProgress(0, 0, true)
    is MessageSendWorkProgress.Determinate -> setProgress(
        progress.total.coerceAtLeast(0),
        progress.current.coerceAtLeast(0),
        false,
    )
}
