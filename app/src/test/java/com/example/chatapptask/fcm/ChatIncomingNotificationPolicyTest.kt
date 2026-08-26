package com.example.chatapptask.fcm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatIncomingNotificationPolicyTest {
    @Test
    fun shouldSuppressForSender_whenIdsMatch() {
        assertTrue(
            ChatIncomingNotificationPolicy.shouldSuppressForSender(
                payloadSenderId = "11111111-1111-1111-1111-111111111111",
                currentUserId = "11111111-1111-1111-1111-111111111111",
            ),
        )
        assertTrue(
            ChatIncomingNotificationPolicy.shouldSuppressForSender(
                payloadSenderId = "  abc  ",
                currentUserId = "abc",
            ),
        )
    }

    @Test
    fun shouldSuppressForSender_falseWhenDifferent() {
        assertFalse(
            ChatIncomingNotificationPolicy.shouldSuppressForSender(
                payloadSenderId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                currentUserId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
            ),
        )
    }

    @Test
    fun shouldSuppressForActiveChat_followsVisibility() {
        assertTrue(ChatIncomingNotificationPolicy.shouldSuppressForActiveChat(true))
        assertFalse(ChatIncomingNotificationPolicy.shouldSuppressForActiveChat(false))
    }

    @Test
    fun notificationId_isDeterministicPerMessageId() {
        val messageId = "cccccccc-cccc-cccc-cccc-cccccccccccc"
        val first = ChatIncomingNotificationPolicy.notificationId(messageId)
        val second = ChatIncomingNotificationPolicy.notificationId(messageId)
        assertEquals(first, second)
        assertEquals(
            first,
            ChatIncomingNotificationPolicy.notificationId("  $messageId  "),
        )
    }

    @Test
    fun notificationId_differsAcrossMessagesAndFromWorkManagerStyleKeys() {
        val a = ChatIncomingNotificationPolicy.notificationId("msg-a")
        val b = ChatIncomingNotificationPolicy.notificationId("msg-b")
        assertNotEquals(a, b)
        // WorkManager uses raw unique-work-name hashCode; incoming uses a distinct prefix.
        assertNotEquals(
            "send-text-message:msg-a".hashCode(),
            ChatIncomingNotificationPolicy.notificationId("msg-a"),
        )
    }

    @Test
    fun notificationBody_prefersTextForMediaWhenPresent() {
        assertEquals(
            "Caption",
            ChatIncomingNotificationPolicy.notificationBody(
                previewKind = ChatMessageFcmPayload.PreviewKind.IMAGE,
                previewText = "Caption",
            ),
        )
        assertEquals(
            "Sent a photo",
            ChatIncomingNotificationPolicy.notificationBody(
                previewKind = ChatMessageFcmPayload.PreviewKind.IMAGE,
                previewText = "  ",
            ),
        )
        assertEquals(
            "Sent a video",
            ChatIncomingNotificationPolicy.notificationBody(
                previewKind = ChatMessageFcmPayload.PreviewKind.VIDEO,
                previewText = "",
            ),
        )
        assertEquals(
            "Sent attachments",
            ChatIncomingNotificationPolicy.notificationBody(
                previewKind = ChatMessageFcmPayload.PreviewKind.MEDIA,
                previewText = "",
            ),
        )
        assertEquals(
            "Hello",
            ChatIncomingNotificationPolicy.notificationBody(
                previewKind = ChatMessageFcmPayload.PreviewKind.TEXT,
                previewText = "Hello",
            ),
        )
        assertEquals(
            "New message",
            ChatIncomingNotificationPolicy.notificationBody(
                previewKind = ChatMessageFcmPayload.PreviewKind.TEXT,
                previewText = "",
            ),
        )
    }

    @Test
    fun notificationTitle_fallsBackWhenUsernameBlank() {
        assertEquals("Ada", ChatIncomingNotificationPolicy.notificationTitle("Ada"))
        assertEquals("Chat", ChatIncomingNotificationPolicy.notificationTitle("  "))
    }

    @Test
    fun groupKey_isStable() {
        assertEquals("chat_messages", ChatIncomingNotificationPolicy.GROUP_KEY)
    }
}
