package com.example.chatapptask.feature.chat.presentation

import com.example.chatapptask.core.domain.model.Message
import com.example.chatapptask.core.domain.model.MessageSendStatus
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSendUxTest {
    private val currentUserId = UUID.fromString("33eed91f-846c-49c8-851d-bca519b01432")
    private val otherUserId = UUID.fromString("44eed91f-846c-49c8-851d-bca519b01432")

    @Test
    fun newOutgoingSendingMessage_scrollsToNewest() {
        assertTrue(
            shouldScrollToOutgoingOptimisticMessage(
                previousNewestMessageId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                newestMessage = message(
                    id = "00000000-0000-0000-0000-000000000002",
                    senderId = currentUserId,
                    status = MessageSendStatus.SENDING,
                ),
                currentUserId = currentUserId,
            ),
        )
    }

    @Test
    fun firstOutgoingSendingMessage_scrollsToNewest() {
        assertTrue(
            shouldScrollToOutgoingOptimisticMessage(
                previousNewestMessageId = null,
                newestMessage = message(
                    id = "00000000-0000-0000-0000-000000000001",
                    senderId = currentUserId,
                    status = MessageSendStatus.SENDING,
                ),
                currentUserId = currentUserId,
            ),
        )
    }

    @Test
    fun sameNewestMessage_doesNotScroll() {
        val newestId = UUID.fromString("00000000-0000-0000-0000-000000000002")
        assertFalse(
            shouldScrollToOutgoingOptimisticMessage(
                previousNewestMessageId = newestId,
                newestMessage = message(
                    id = newestId.toString(),
                    senderId = currentUserId,
                    status = MessageSendStatus.SENDING,
                ),
                currentUserId = currentUserId,
            ),
        )
    }

    @Test
    fun incomingNewestMessage_doesNotScroll() {
        assertFalse(
            shouldScrollToOutgoingOptimisticMessage(
                previousNewestMessageId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                newestMessage = message(
                    id = "00000000-0000-0000-0000-000000000003",
                    senderId = otherUserId,
                    status = MessageSendStatus.SENT,
                ),
                currentUserId = currentUserId,
            ),
        )
    }

    @Test
    fun newlyLoadedOutgoingSentHistory_doesNotScroll() {
        assertFalse(
            shouldScrollToOutgoingOptimisticMessage(
                previousNewestMessageId = null,
                newestMessage = message(
                    id = "00000000-0000-0000-0000-000000000004",
                    senderId = currentUserId,
                    status = MessageSendStatus.SENT,
                ),
                currentUserId = currentUserId,
            ),
        )
    }

    @Test
    fun olderPageAppended_doesNotScrollWhenNewestIdUnchanged() {
        val newestId = UUID.fromString("00000000-0000-0000-0000-000000000005")
        assertFalse(
            shouldScrollToOutgoingOptimisticMessage(
                previousNewestMessageId = newestId,
                newestMessage = message(
                    id = newestId.toString(),
                    senderId = currentUserId,
                    status = MessageSendStatus.SENT,
                ),
                currentUserId = currentUserId,
            ),
        )
    }

    @Test
    fun sendStatusPromotion_doesNotScroll() {
        val newestId = UUID.fromString("00000000-0000-0000-0000-000000000006")
        assertFalse(
            shouldScrollToOutgoingOptimisticMessage(
                previousNewestMessageId = newestId,
                newestMessage = message(
                    id = newestId.toString(),
                    senderId = currentUserId,
                    status = MessageSendStatus.SENT,
                ),
                currentUserId = currentUserId,
            ),
        )
    }

    @Test
    fun incomingWhileNearNewest_scrollsToNewest() {
        assertTrue(
            shouldScrollToIncomingLiveMessage(
                previousNewestMessageId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                newestMessage = message(
                    id = "00000000-0000-0000-0000-000000000003",
                    senderId = otherUserId,
                    status = MessageSendStatus.SENT,
                ),
                isNearNewest = true,
            ),
        )
    }

    @Test
    fun incomingWhileReadingOlderMessages_doesNotScroll() {
        assertFalse(
            shouldScrollToIncomingLiveMessage(
                previousNewestMessageId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                newestMessage = message(
                    id = "00000000-0000-0000-0000-000000000003",
                    senderId = otherUserId,
                    status = MessageSendStatus.SENT,
                ),
                isNearNewest = false,
            ),
        )
    }

    @Test
    fun outgoingOptimisticSend_stillScrollsIndependentlyOfIncomingRule() {
        assertTrue(
            shouldScrollToOutgoingOptimisticMessage(
                previousNewestMessageId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                newestMessage = message(
                    id = "00000000-0000-0000-0000-000000000002",
                    senderId = currentUserId,
                    status = MessageSendStatus.SENDING,
                ),
                currentUserId = currentUserId,
            ),
        )
        assertFalse(
            shouldScrollToIncomingLiveMessage(
                previousNewestMessageId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                newestMessage = message(
                    id = "00000000-0000-0000-0000-000000000002",
                    senderId = currentUserId,
                    status = MessageSendStatus.SENDING,
                ),
                isNearNewest = false,
            ),
        )
    }

    @Test
    fun pagination_doesNotTriggerIncomingNewestScroll() {
        val newestId = UUID.fromString("00000000-0000-0000-0000-000000000005")
        assertFalse(
            shouldScrollToIncomingLiveMessage(
                previousNewestMessageId = newestId,
                newestMessage = message(
                    id = newestId.toString(),
                    senderId = otherUserId,
                    status = MessageSendStatus.SENT,
                ),
                isNearNewest = true,
            ),
        )
    }

    @Test
    fun initialLoad_doesNotTriggerIncomingNewestScroll() {
        assertFalse(
            shouldScrollToIncomingLiveMessage(
                previousNewestMessageId = null,
                newestMessage = message(
                    id = "00000000-0000-0000-0000-000000000004",
                    senderId = otherUserId,
                    status = MessageSendStatus.SENT,
                ),
                isNearNewest = true,
            ),
        )
    }

    private fun message(
        id: String,
        senderId: UUID,
        status: MessageSendStatus,
    ): Message = Message(
        id = UUID.fromString(id),
        senderId = senderId,
        textContent = "Message",
        createdAt = Instant.parse("2026-08-25T12:00:00Z"),
        updatedAt = Instant.parse("2026-08-25T12:00:00Z"),
        media = emptyList(),
        sendStatus = status,
    )
}
