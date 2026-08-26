package com.example.chatapptask.data.chat.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaUploadFailureClassificationTest {
    @Test
    fun payloadTooLargeAnd413_arePermanent() {
        assertTrue(isPermanentStorageRejection(IllegalStateException("Payload too large")))
        assertTrue(isPermanentStorageRejection(IllegalStateException("Object exceeded the maximum allowed size")))
        assertTrue(
            isPermanentStorageRejection(
                PermanentMediaUploadException("Each photo or video must be 50 MB or smaller."),
            ),
        )
    }

    @Test
    fun transientNetworkAndServerErrors_areNotPermanent() {
        assertFalse(isPermanentStorageRejection(IllegalStateException("storage unavailable")))
        assertFalse(isPermanentStorageRejection(IllegalStateException("timeout")))
        assertFalse(isPermanentStorageRejection(IllegalStateException("503 Service Unavailable")))
    }
}
