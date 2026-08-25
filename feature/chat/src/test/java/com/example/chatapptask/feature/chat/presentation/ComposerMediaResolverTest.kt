package com.example.chatapptask.feature.chat.presentation

import com.example.chatapptask.core.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComposerMediaResolverTest {
    @Test
    fun mediaTypeForMime_mapsImageAndVideo() {
        assertEquals(MediaType.IMAGE, mediaTypeForMime("image/jpeg"))
        assertEquals(MediaType.IMAGE, mediaTypeForMime("IMAGE/PNG"))
        assertEquals(MediaType.VIDEO, mediaTypeForMime("video/mp4"))
        assertEquals(MediaType.VIDEO, mediaTypeForMime("Video/QuickTime"))
    }

    @Test
    fun mediaTypeForMime_rejectsUnsupportedTypes() {
        assertNull(mediaTypeForMime("application/pdf"))
        assertNull(mediaTypeForMime("audio/mpeg"))
        assertNull(mediaTypeForMime(""))
        assertNull(mediaTypeForMime("text/plain"))
    }

    @Test
    fun composerAttachment_mapsToPendingMediaWithoutDimensions() {
        val attachment = ComposerAttachment(
            uri = "content://picker/1",
            mediaType = MediaType.VIDEO,
            mimeType = "video/mp4",
        )

        val pending = attachment.toPendingMedia()

        assertEquals("content://picker/1", pending.localUri)
        assertEquals(MediaType.VIDEO, pending.mediaType)
        assertEquals("video/mp4", pending.mimeType)
        assertNull(pending.sizeBytes)
        assertNull(pending.width)
        assertNull(pending.height)
    }
}
