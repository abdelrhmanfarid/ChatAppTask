package com.example.chatapptask.fcm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatScreenVisibilityTest {
    @Test
    fun visibility_toggles() {
        ChatScreenVisibility.setVisible(false)
        assertFalse(ChatScreenVisibility.isVisible())
        ChatScreenVisibility.setVisible(true)
        assertTrue(ChatScreenVisibility.isVisible())
        ChatScreenVisibility.setVisible(false)
        assertFalse(ChatScreenVisibility.isVisible())
    }
}
