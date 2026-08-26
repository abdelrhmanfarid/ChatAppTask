package com.example.chatapptask.data.chat.push

import com.example.chatapptask.core.common.identity.UserIdentityStore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserIdentityCurrentPushUserMatcherTest {
    private val currentUserId = UUID.fromString("11111111-1111-1111-1111-111111111111")

    @Test
    fun isCurrentUser_trueWhenSenderMatchesIdentity() = runBlocking {
        val matcher = UserIdentityCurrentPushUserMatcher(FixedUserIdentityStore(currentUserId))

        assertTrue(matcher.isCurrentUser(currentUserId.toString()))
        assertTrue(matcher.isCurrentUser("  $currentUserId  "))
    }

    @Test
    fun isCurrentUser_falseWhenSenderDiffers() = runBlocking {
        val matcher = UserIdentityCurrentPushUserMatcher(FixedUserIdentityStore(currentUserId))

        assertFalse(
            matcher.isCurrentUser("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
        )
    }

    private class FixedUserIdentityStore(
        private val userId: UUID,
    ) : UserIdentityStore {
        override suspend fun getOrCreateUserId(): UUID = userId
    }
}
