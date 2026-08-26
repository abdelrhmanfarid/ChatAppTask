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

    @Test
    fun childNotificationIdentity_sharesGroupKeyAndIsNotSummary() {
        val messageId = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"
        val identity = ChatIncomingNotificationPolicy.childNotificationIdentity(messageId)
        assertEquals(ChatIncomingNotificationPolicy.GROUP_KEY, identity.groupKey)
        assertEquals(ChatIncomingNotificationPolicy.notificationId(messageId), identity.id)
        assertFalse(identity.isGroupSummary)
        assertEquals(
            ChatIncomingNotificationPolicy.GROUP_KEY,
            ChatIncomingNotificationPolicy.childNotificationIdentity("other-msg").groupKey,
        )
    }

    @Test
    fun summaryNotificationIdentity_usesFixedIdAndIsGroupSummary() {
        val identity = ChatIncomingNotificationPolicy.summaryNotificationIdentity()
        assertEquals(ChatIncomingNotificationPolicy.GROUP_SUMMARY_NOTIFICATION_ID, identity.id)
        assertEquals(ChatIncomingNotificationPolicy.GROUP_KEY, identity.groupKey)
        assertTrue(identity.isGroupSummary)
        assertEquals(
            "incoming-chat-group-summary".hashCode(),
            ChatIncomingNotificationPolicy.GROUP_SUMMARY_NOTIFICATION_ID,
        )
    }

    @Test
    fun summaryNotificationId_differsFromChildAndWorkManagerStyleKeys() {
        val childId = ChatIncomingNotificationPolicy.notificationId("msg-a")
        val summaryId = ChatIncomingNotificationPolicy.GROUP_SUMMARY_NOTIFICATION_ID
        assertNotEquals(childId, summaryId)
        assertNotEquals("send-text-message:msg-a".hashCode(), summaryId)
        assertNotEquals("send-media-message:msg-a".hashCode(), summaryId)
        assertNotEquals(
            "incoming-chat-message:msg-a".hashCode(),
            summaryId,
        )
    }

    @Test
    fun countActiveGroupChildren_ignoresOtherGroupsAndSummaries() {
        val group = ChatIncomingNotificationPolicy.GROUP_KEY
        assertEquals(
            2,
            ChatIncomingNotificationPolicy.countActiveGroupChildren(
                listOf(
                    group to false,
                    group to false,
                    group to true,
                    "other_group" to false,
                    null to false,
                ),
            ),
        )
        assertEquals(
            0,
            ChatIncomingNotificationPolicy.countActiveGroupChildren(
                listOf(group to true),
            ),
        )
    }

    @Test
    fun groupSummaryMessageCount_coercesAtLeastOne() {
        assertEquals(1, ChatIncomingNotificationPolicy.groupSummaryMessageCount(0))
        assertEquals(1, ChatIncomingNotificationPolicy.groupSummaryMessageCount(1))
        assertEquals(3, ChatIncomingNotificationPolicy.groupSummaryMessageCount(3))
    }
}
