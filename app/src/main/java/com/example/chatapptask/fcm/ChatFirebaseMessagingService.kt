package com.example.chatapptask.fcm

import com.example.chatapptask.data.chat.push.PushInstallationRegistrar
import com.google.firebase.messaging.FirebaseMessagingService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * FCM entry point for Firebase Installation ID (FID) registration callbacks.
 * Caches and uploads the FID through [PushInstallationRegistrar]; does not handle messages.
 */
@AndroidEntryPoint
class ChatFirebaseMessagingService : FirebaseMessagingService() {
    @Inject
    lateinit var pushInstallationRegistrar: PushInstallationRegistrar

    override fun onRegistered(installationId: String) {
        // Callback runs on a worker thread; cache + schedule IO without blocking here.
        pushInstallationRegistrar.onRegisteredAsync(installationId)
    }
}
