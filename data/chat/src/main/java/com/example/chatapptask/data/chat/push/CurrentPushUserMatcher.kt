package com.example.chatapptask.data.chat.push

/**
 * Compares an FCM payload sender id to the current deterministic device identity.
 * Keeps [com.example.chatapptask.core.common.identity.UserIdentityStore] inside `:data:chat`
 * so `:app` FCM code does not depend on `:core:common`.
 */
interface CurrentPushUserMatcher {
    /**
     * Returns true when [senderId] matches the current local identity (trimmed string equality).
     * Propagates identity-store failures to the caller.
     */
    suspend fun isCurrentUser(senderId: String): Boolean
}
