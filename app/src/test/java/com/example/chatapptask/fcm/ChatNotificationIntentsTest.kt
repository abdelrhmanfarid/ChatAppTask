package com.example.chatapptask.fcm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatNotificationIntentsTest {
    @Test
    fun openChatTarget_carriesActionAndMessageId() {
        val target = ChatNotificationIntents.openChatTarget("  msg-123  ")
        assertEquals(ChatNotificationIntents.ACTION_OPEN_CHAT, target.action)
        assertEquals("msg-123", target.messageId)
    }

    @Test
    fun extractOpenChatMessageId_acceptsOnce() {
        assertEquals(
            "mid-1",
            ChatNotificationIntents.extractOpenChatMessageId(
                action = ChatNotificationIntents.ACTION_OPEN_CHAT,
                messageIdExtra = " mid-1 ",
                alreadyHandled = false,
            ),
        )
    }

    @Test
    fun extractOpenChatMessageId_rejectsWrongActionBlankOrAlreadyHandled() {
        assertNull(
            ChatNotificationIntents.extractOpenChatMessageId(
                action = "android.intent.action.MAIN",
                messageIdExtra = "mid-1",
                alreadyHandled = false,
            ),
        )
        assertNull(
            ChatNotificationIntents.extractOpenChatMessageId(
                action = ChatNotificationIntents.ACTION_OPEN_CHAT,
                messageIdExtra = "  ",
                alreadyHandled = false,
            ),
        )
        assertNull(
            ChatNotificationIntents.extractOpenChatMessageId(
                action = ChatNotificationIntents.ACTION_OPEN_CHAT,
                messageIdExtra = "mid-1",
                alreadyHandled = true,
            ),
        )
    }

    @Test
    fun pendingIntentRequestCode_matchesNotificationIdNamespace() {
        val messageId = "dddddddd-dddd-dddd-dddd-dddddddddddd"
        assertEquals(
            ChatIncomingNotificationPolicy.notificationId(messageId),
            ChatIncomingNotificationPolicy.notificationId(messageId),
        )
    }
}
