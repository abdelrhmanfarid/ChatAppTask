package com.example.chatapptask.fcm

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tracks whether the Chat screen is visibly resumed in the foreground.
 * Used only to suppress incoming FCM notifications while Realtime already shows the message.
 * Not a global navigation or Chat UI state store.
 */
object ChatScreenVisibility {
    private val visible = AtomicBoolean(false)

    fun setVisible(isVisible: Boolean) {
        visible.set(isVisible)
    }

    fun isVisible(): Boolean = visible.get()
}
