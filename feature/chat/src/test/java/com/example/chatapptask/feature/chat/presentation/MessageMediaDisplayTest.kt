package com.example.chatapptask.feature.chat.presentation

import com.example.chatapptask.core.domain.model.MediaType
import com.example.chatapptask.core.domain.model.MediaUploadStatus
import com.example.chatapptask.core.domain.model.Message
import com.example.chatapptask.core.domain.model.MessageMedia
import com.example.chatapptask.core.domain.model.MessageSendStatus
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageMediaDisplayTest {
    private val publicUrlFor: (String) -> String? = { path ->
        "https://example.supabase.co/storage/v1/object/public/chat-media/$path"
    }

    @Test
    fun mediaOnlyMessage_isNoLongerEmptyContent() {
        val message = message(
            text = null,
            media = listOf(imageMedia(position = 0, storagePath = "msg/image.jpg")),
        )

        assertTrue(message.textContent.isNullOrBlank())
        assertTrue(messageHasRenderableBody(message))
        assertEquals(1, messageMediaItemsForDisplay(message, publicUrlFor).size)
    }

    @Test
    fun textOnlyMessage_keepsTextAndHasNoMediaItems() {
        val message = message(text = "Hello there", media = emptyList())

        assertEquals("Hello there", message.textContent)
        assertTrue(messageHasRenderableBody(message))
        assertTrue(messageMediaItemsForDisplay(message, publicUrlFor).isEmpty())
    }

    @Test
    fun mediaAndText_includesBoth() {
        val message = message(
            text = "Look at this",
            media = listOf(imageMedia(position = 0, storagePath = "msg/image.jpg")),
        )
        val items = messageMediaItemsForDisplay(message, publicUrlFor)

        assertEquals("Look at this", message.textContent)
        assertTrue(messageHasRenderableBody(message))
        assertEquals(1, items.size)
        assertEquals(MediaType.IMAGE, items.single().mediaType)
        assertEquals(
            "https://example.supabase.co/storage/v1/object/public/chat-media/msg/image.jpg",
            items.single().displayUri,
        )
    }

    @Test
    fun attachments_preservePositionOrder() {
        val first = imageMedia(
            id = "00000000-0000-0000-0000-0000000000aa",
            position = 2,
            storagePath = "msg/c.jpg",
        )
        val second = imageMedia(
            id = "00000000-0000-0000-0000-0000000000bb",
            position = 0,
            storagePath = "msg/a.jpg",
        )
        val third = imageMedia(
            id = "00000000-0000-0000-0000-0000000000cc",
            position = 1,
            storagePath = "msg/b.jpg",
        )
        val items = messageMediaItemsForDisplay(
            message(media = listOf(first, second, third)),
            publicUrlFor,
        )

        assertEquals(listOf(0, 1, 2), items.map(MessageMediaItemUi::position))
        assertEquals(
            listOf(
                UUID.fromString("00000000-0000-0000-0000-0000000000bb"),
                UUID.fromString("00000000-0000-0000-0000-0000000000cc"),
                UUID.fromString("00000000-0000-0000-0000-0000000000aa"),
            ),
            items.map(MessageMediaItemUi::id),
        )
    }

    @Test
    fun sendingOutgoingImage_prefersLocalUri() {
        val message = message(
            sendStatus = MessageSendStatus.SENDING,
            media = listOf(
                imageMedia(
                    storagePath = "msg/image.jpg",
                    localUri = "file:///outgoing-media/msg/image.jpg",
                    uploadStatus = MediaUploadStatus.UPLOADING,
                ),
            ),
        )

        val item = messageMediaItemsForDisplay(message, publicUrlFor).single()

        assertEquals("file:///outgoing-media/msg/image.jpg", item.displayUri)
        assertEquals(MediaType.IMAGE, item.mediaType)
    }

    @Test
    fun sentImage_prefersRemotePublicUrl() {
        val message = message(
            sendStatus = MessageSendStatus.SENT,
            media = listOf(
                imageMedia(
                    storagePath = "msg/image.jpg",
                    localUri = "file:///outgoing-media/msg/image.jpg",
                    uploadStatus = MediaUploadStatus.UPLOADED,
                ),
            ),
        )

        assertEquals(
            "https://example.supabase.co/storage/v1/object/public/chat-media/msg/image.jpg",
            messageMediaItemsForDisplay(message, publicUrlFor).single().displayUri,
        )
    }

    @Test
    fun outgoingAndIncomingSentMedia_useTheSameDisplayModel() {
        val media = listOf(imageMedia(storagePath = "msg/image.jpg", localUri = null))
        val outgoing = message(
            senderId = CURRENT_USER_ID,
            sendStatus = MessageSendStatus.SENT,
            media = media,
        )
        val incoming = message(
            senderId = OTHER_USER_ID,
            sendStatus = MessageSendStatus.SENT,
            media = media,
        )

        assertEquals(
            messageMediaItemsForDisplay(outgoing, publicUrlFor),
            messageMediaItemsForDisplay(incoming, publicUrlFor),
        )
        assertNotEquals(outgoing.senderId, incoming.senderId)
    }

    @Test
    fun failedOutgoingImage_prefersLocalUri() {
        val message = message(
            sendStatus = MessageSendStatus.FAILED,
            media = listOf(
                imageMedia(
                    storagePath = "msg/image.jpg",
                    localUri = "file:///outgoing-media/msg/image.jpg",
                    uploadStatus = MediaUploadStatus.FAILED,
                ),
            ),
        )

        assertEquals(
            "file:///outgoing-media/msg/image.jpg",
            messageMediaItemsForDisplay(message, publicUrlFor).single().displayUri,
        )
        assertEquals(MessageSendStatus.FAILED, message.sendStatus)
    }

    @Test
    fun sendingFailedAndSent_keepMessageLevelStatus() {
        val sending = message(sendStatus = MessageSendStatus.SENDING, media = listOf(imageMedia()))
        val failed = message(sendStatus = MessageSendStatus.FAILED, media = listOf(imageMedia()))
        val sent = message(sendStatus = MessageSendStatus.SENT, media = listOf(imageMedia()))

        assertEquals(MessageSendStatus.SENDING, sending.sendStatus)
        assertEquals(MessageSendStatus.FAILED, failed.sendStatus)
        assertEquals(MessageSendStatus.SENT, sent.sendStatus)
        assertTrue(messageHasRenderableBody(sending))
        assertTrue(messageHasRenderableBody(failed))
        assertTrue(messageHasRenderableBody(sent))
    }

    @Test
    fun missingOrBlankUrl_producesSafeNullDisplayUri() {
        val missing = message(
            sendStatus = MessageSendStatus.SENT,
            media = listOf(imageMedia(storagePath = null, localUri = null)),
        )
        val blank = message(
            sendStatus = MessageSendStatus.SENT,
            media = listOf(imageMedia(storagePath = "  ", localUri = "")),
        )
        val factoryFailure = message(
            sendStatus = MessageSendStatus.SENT,
            media = listOf(imageMedia(storagePath = "msg/missing.jpg")),
        )

        assertNull(messageMediaItemsForDisplay(missing, publicUrlFor).single().displayUri)
        assertNull(messageMediaItemsForDisplay(blank, publicUrlFor).single().displayUri)
        assertNull(messageMediaItemsForDisplay(factoryFailure) { null }.single().displayUri)
        assertTrue(messageHasRenderableBody(missing))
    }

    @Test
    fun videoAttachment_usesVideoPresentationPath() {
        val message = message(
            media = listOf(
                imageMedia(
                    mediaType = MediaType.VIDEO,
                    mimeType = "video/mp4",
                    storagePath = "msg/video.mp4",
                ),
            ),
        )
        val item = messageMediaItemsForDisplay(message, publicUrlFor).single()

        assertEquals(MediaType.VIDEO, item.mediaType)
        assertFalse(item.displayUri.isNullOrBlank())
    }

    private fun message(
        text: String? = null,
        sendStatus: MessageSendStatus = MessageSendStatus.SENT,
        senderId: UUID = CURRENT_USER_ID,
        media: List<MessageMedia> = emptyList(),
    ): Message = Message(
        id = MESSAGE_ID,
        senderId = senderId,
        textContent = text,
        createdAt = Instant.parse("2026-08-25T12:00:00Z"),
        updatedAt = Instant.parse("2026-08-25T12:00:00Z"),
        media = media,
        sendStatus = sendStatus,
    )

    private fun imageMedia(
        id: String = "00000000-0000-0000-0000-0000000000a1",
        position: Int = 0,
        storagePath: String? = "msg/image.jpg",
        localUri: String? = null,
        mediaType: MediaType = MediaType.IMAGE,
        mimeType: String = "image/jpeg",
        uploadStatus: MediaUploadStatus = MediaUploadStatus.UPLOADED,
    ): MessageMedia = MessageMedia(
        id = UUID.fromString(id),
        messageId = MESSAGE_ID,
        storagePath = storagePath,
        mediaType = mediaType,
        mimeType = mimeType,
        position = position,
        sizeBytes = null,
        width = null,
        height = null,
        localUri = localUri,
        uploadStatus = uploadStatus,
    )

    private companion object {
        val MESSAGE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val CURRENT_USER_ID: UUID = UUID.fromString("33eed91f-846c-49c8-851d-bca519b01432")
        val OTHER_USER_ID: UUID = UUID.fromString("44eed91f-846c-49c8-851d-bca519b01432")
    }
}
