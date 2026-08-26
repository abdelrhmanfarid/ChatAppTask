package com.example.chatapptask.fcm

import android.util.Log
import com.example.chatapptask.data.chat.push.CurrentPushUserMatcher
import com.example.chatapptask.data.chat.push.PushInstallationRegistrar
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

/**
 * FCM entry point: FID registration callbacks and data-only incoming chat notifications.
 * Does not write Room, fetch messages, download media, or replace Supabase Realtime.
 */
@AndroidEntryPoint
class ChatFirebaseMessagingService : FirebaseMessagingService() {
    @Inject
    lateinit var pushInstallationRegistrar: PushInstallationRegistrar

    @Inject
    lateinit var currentPushUserMatcher: CurrentPushUserMatcher

    override fun onRegistered(installationId: String) {
        // Callback runs on a worker thread; cache + schedule IO without blocking here.
        pushInstallationRegistrar.onRegisteredAsync(installationId)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val payload = ChatMessageFcmPayload.parse(message.data)
        if (payload == null) {
            Log.d(TAG, "FCM chat payload rejected or malformed; ignored")
            return
        }
        Log.d(TAG, "FCM chat payload accepted")

        val isFromCurrentUser = runCatching {
            runBlocking { currentPushUserMatcher.isCurrentUser(payload.senderId) }
        }.getOrElse {
            Log.d(TAG, "FCM chat payload accepted but identity unavailable; notification skipped")
            return
        }

        if (isFromCurrentUser) {
            Log.d(TAG, "Incoming chat notification suppressed due to sender")
            return
        }

        if (ChatIncomingNotificationPolicy.shouldSuppressForActiveChat(ChatScreenVisibility.isVisible())) {
            Log.d(TAG, "Incoming chat notification suppressed because Chat is active")
            return
        }

        val posted = ChatIncomingNotifications.post(applicationContext, payload)
        if (posted) {
            Log.d(TAG, "Incoming chat notification posted")
        } else {
            Log.d(TAG, "Incoming chat notification not posted (permission or manager unavailable)")
        }
    }

    private companion object {
        const val TAG = "ChatFcm"
    }
}
