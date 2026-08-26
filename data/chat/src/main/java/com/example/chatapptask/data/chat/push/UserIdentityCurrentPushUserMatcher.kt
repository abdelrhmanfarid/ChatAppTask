package com.example.chatapptask.data.chat.push

import com.example.chatapptask.core.common.identity.UserIdentityStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserIdentityCurrentPushUserMatcher @Inject constructor(
    private val userIdentityStore: UserIdentityStore,
) : CurrentPushUserMatcher {
    override suspend fun isCurrentUser(senderId: String): Boolean {
        val currentUserId = userIdentityStore.getOrCreateUserId().toString()
        return senderId.trim() == currentUserId.trim()
    }
}
