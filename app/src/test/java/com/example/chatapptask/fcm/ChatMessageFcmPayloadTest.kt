package com.example.chatapptask.fcm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChatMessageFcmPayloadTest {
    @Test
    fun parse_acceptsValidChatMessageV1() {
        val payload = ChatMessageFcmPayload.parse(
            mapOf(
                "type" to "chat_message",
                "schema_version" to "1",
                "message_id" to "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                "sender_id" to "11111111-2222-3333-4444-555555555555",
                "sender_username" to "Ada",
                "preview_kind" to "text",
                "preview_text" to "Hello",
            ),
        )

        assertNotNull(payload)
        assertEquals("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", payload!!.messageId)
        assertEquals("11111111-2222-3333-4444-555555555555", payload.senderId)
        assertEquals("Ada", payload.senderUsername)
        assertEquals(ChatMessageFcmPayload.PreviewKind.TEXT, payload.previewKind)
        assertEquals("Hello", payload.previewText)
    }

    @Test
    fun parse_rejectsWrongType() {
        assertNull(
            ChatMessageFcmPayload.parse(
                validBase() + ("type" to "other"),
            ),
        )
    }

    @Test
    fun parse_rejectsWrongSchemaVersion() {
        assertNull(
            ChatMessageFcmPayload.parse(
                validBase() + ("schema_version" to "2"),
            ),
        )
    }

    @Test
    fun parse_rejectsMissingMessageIdOrSenderId() {
        assertNull(
            ChatMessageFcmPayload.parse(
                validBase() - "message_id",
            ),
        )
        assertNull(
            ChatMessageFcmPayload.parse(
                validBase() + ("message_id" to "  "),
            ),
        )
        assertNull(
            ChatMessageFcmPayload.parse(
                validBase() - "sender_id",
            ),
        )
    }

    @Test
    fun parse_rejectsUnknownPreviewKind() {
        assertNull(
            ChatMessageFcmPayload.parse(
                validBase() + ("preview_kind" to "audio"),
            ),
        )
    }

    @Test
    fun parse_acceptsAllPreviewKindsAndEmptyPreviewText() {
        listOf("text", "image", "video", "media", "IMAGE", " Video ").forEach { kind ->
            val payload = ChatMessageFcmPayload.parse(
                validBase() + ("preview_kind" to kind) + ("preview_text" to ""),
            )
            assertNotNull(kind, payload)
        }
    }

    @Test
    fun parse_trimsFields() {
        val payload = ChatMessageFcmPayload.parse(
            mapOf(
                "type" to " chat_message ",
                "schema_version" to " 1 ",
                "message_id" to " mid ",
                "sender_id" to " sid ",
                "sender_username" to "  Name  ",
                "preview_kind" to " text ",
                "preview_text" to "  hi  ",
            ),
        )
        assertNotNull(payload)
        assertEquals("mid", payload!!.messageId)
        assertEquals("sid", payload.senderId)
        assertEquals("Name", payload.senderUsername)
        assertEquals("hi", payload.previewText)
    }

    private fun validBase(): Map<String, String> = mapOf(
        "type" to "chat_message",
        "schema_version" to "1",
        "message_id" to "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
        "sender_id" to "11111111-2222-3333-4444-555555555555",
        "sender_username" to "Ada",
        "preview_kind" to "text",
        "preview_text" to "Hello",
    )
}
