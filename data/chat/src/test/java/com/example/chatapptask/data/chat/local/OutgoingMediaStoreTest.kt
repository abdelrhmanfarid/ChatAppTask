package com.example.chatapptask.data.chat.local

import org.junit.Assert.assertEquals
import org.junit.Test

class OutgoingMediaStoreTest {
    @Test
    fun fileExtensionFor_usesMimeTypeWhenKnown() {
        assertEquals("jpg", fileExtensionFor("image/jpeg", "content://picker/photo"))
        assertEquals("png", fileExtensionFor("image/png; charset=utf-8", "content://picker/photo"))
        assertEquals("mp4", fileExtensionFor("video/mp4", "content://picker/clip"))
    }

    @Test
    fun fileExtensionFor_fallsBackToUriThenBin() {
        assertEquals("webp", fileExtensionFor("application/octet-stream", "content://picker/file.webp"))
        assertEquals("bin", fileExtensionFor("application/octet-stream", "content://picker/file"))
    }
}
