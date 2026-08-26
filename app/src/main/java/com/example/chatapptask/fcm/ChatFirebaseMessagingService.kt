package com.example.chatapptask.fcm

import com.google.firebase.messaging.FirebaseMessagingService

/**
 * FCM entry point for Firebase Installation ID (FID) registration callbacks.
 *
 * Stage 1B: observe [onRegistered] only. Backend upload of the FID is deferred
 * to the next FCM stage.
 */
class ChatFirebaseMessagingService : FirebaseMessagingService() {
    override fun onRegistered(installationId: String) {
        // Intentionally no-op: do not persist, log, or upload the FID yet.
        // Backend push-registration is added in the next stage.
    }
}
