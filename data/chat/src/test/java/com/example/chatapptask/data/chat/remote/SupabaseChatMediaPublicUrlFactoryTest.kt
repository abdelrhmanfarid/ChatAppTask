package com.example.chatapptask.data.chat.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupabaseChatMediaPublicUrlFactoryTest {
    @Test
    fun normalizedPath_keepsUploadStorageFormat() {
        val messageId = "11111111-1111-1111-1111-111111111111"
        val mediaId = "22222222-2222-2222-2222-222222222222"
        val path = "$messageId/$mediaId.jpg"

        assertEquals(path, normalizedChatMediaStoragePath(path))
        assertEquals(path, normalizedChatMediaStoragePath("  $path  "))
        assertEquals(path, normalizedChatMediaStoragePath("/$path"))
        assertEquals(
            "$messageId/$mediaId.png",
            normalizedChatMediaStoragePath("$messageId/$mediaId.png"),
        )
        assertEquals(
            "$messageId/$mediaId.mp4",
            normalizedChatMediaStoragePath("$messageId/$mediaId.mp4"),
        )
    }

    @Test
    fun normalizedPath_rejectsBlankAndAbsoluteUrls() {
        assertNull(normalizedChatMediaStoragePath("   "))
        assertNull(normalizedChatMediaStoragePath(""))
        assertNull(normalizedChatMediaStoragePath("https://example.invalid/storage/v1/object/public/chat-media/msg/image.jpg"))
        assertNull(normalizedChatMediaStoragePath("http://example.invalid/chat-media/msg/image.jpg"))
    }
}
