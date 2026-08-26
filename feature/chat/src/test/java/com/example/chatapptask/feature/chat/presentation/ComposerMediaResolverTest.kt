package com.example.chatapptask.feature.chat.presentation

import com.example.chatapptask.core.domain.model.MAX_MEDIA_ITEM_BYTES
import com.example.chatapptask.core.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun composerAttachment_mapsToPendingMediaWithResolvedSize() {
        val attachment = ComposerAttachment(
            uri = "content://picker/1",
            mediaType = MediaType.VIDEO,
            mimeType = "video/mp4",
            sizeBytes = 12L,
        )

        val pending = attachment.toPendingMedia()

        assertEquals("content://picker/1", pending.localUri)
        assertEquals(MediaType.VIDEO, pending.mediaType)
        assertEquals("video/mp4", pending.mimeType)
        assertEquals(12L, pending.sizeBytes)
        assertNull(pending.width)
        assertNull(pending.height)
    }

    @Test
    fun oversizedMedia_isRejectedAtLimitBoundary() {
        assertFalse(isOversizedMedia(null))
        assertFalse(isOversizedMedia(0L))
        assertFalse(isOversizedMedia(MAX_MEDIA_ITEM_BYTES - 1))
        assertFalse(isOversizedMedia(MAX_MEDIA_ITEM_BYTES))
        assertTrue(isOversizedMedia(MAX_MEDIA_ITEM_BYTES + 1))
        assertTrue(isOversizedMedia(59_155_328L))
    }

    @Test
    fun resolvedMediaSizeBytes_prefersOpenableColumnThenAssetLength() {
        assertEquals(12L, resolvedMediaSizeBytes(openableColumnSize = 12L, assetFileLength = 99L))
        assertEquals(99L, resolvedMediaSizeBytes(openableColumnSize = -1L, assetFileLength = 99L))
        assertEquals(99L, resolvedMediaSizeBytes(openableColumnSize = null, assetFileLength = 99L))
        assertNull(resolvedMediaSizeBytes(openableColumnSize = -1L, assetFileLength = -1L))
        assertNull(resolvedMediaSizeBytes(openableColumnSize = null, assetFileLength = null))
    }
}
