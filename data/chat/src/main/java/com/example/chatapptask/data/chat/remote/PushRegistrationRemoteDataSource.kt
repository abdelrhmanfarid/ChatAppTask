package com.example.chatapptask.data.chat.remote

import java.util.UUID

interface PushRegistrationRemoteDataSource {
    /**
     * Invokes the `register-push` Edge Function.
     * Completes normally on HTTP 2xx; throws on transport or non-success responses.
     */
    suspend fun registerInstallation(
        ownerId: UUID,
        installationId: String,
    )
}
