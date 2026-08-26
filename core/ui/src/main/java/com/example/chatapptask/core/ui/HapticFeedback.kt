package com.example.chatapptask.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Wraps [action] with a short context-click haptic when invoked.
 * Use only for deliberate user confirmations (send, remove, retry).
 */
@Composable
fun rememberHapticAction(action: () -> Unit): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(action, haptic) {
        {
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            action()
        }
    }
}
