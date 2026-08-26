package com.example.chatapptask.data.chat.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupabaseProfileImagePublicUrlFactoryTest {
    @Test
    fun normalizedPath_keepsBucketRelativeFormat() {
        val userId = "550e8400-e29b-41d4-a716-446655440000"
        val path = "$userId/avatar.jpg"

        assertEquals(path, normalizedProfileImagePath(path))
        assertEquals(path, normalizedProfileImagePath("  $path  "))
        assertEquals(path, normalizedProfileImagePath("/$path"))
        assertEquals(
            "$userId/avatar.png",
            normalizedProfileImagePath("$userId/avatar.png"),
        )
    }

    @Test
    fun normalizedPath_rejectsNullBlankAndAbsoluteUrls() {
        assertNull(normalizedProfileImagePath(null))
        assertNull(normalizedProfileImagePath("   "))
        assertNull(normalizedProfileImagePath(""))
        assertNull(
            normalizedProfileImagePath(
                "https://example.invalid/storage/v1/object/public/profile-images/user/avatar.jpg",
            ),
        )
        assertNull(
            normalizedProfileImagePath(
                "http://example.invalid/profile-images/user/avatar.jpg",
            ),
        )
    }

    @Test
    fun profileImagesBucket_isDistinctFromChatMedia() {
        assertEquals("profile-images", PROFILE_IMAGES_BUCKET)
        assertNotEquals("chat-media", PROFILE_IMAGES_BUCKET)
    }
}
