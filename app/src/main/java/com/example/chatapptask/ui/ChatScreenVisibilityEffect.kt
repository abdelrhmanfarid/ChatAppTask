package com.example.chatapptask.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.chatapptask.fcm.ChatScreenVisibility

/**
 * Marks Chat as visibly active while this destination is resumed.
 * Cleared on pause/dispose so background or non-Chat screens allow incoming notifications.
 */
@Composable
internal fun ChatScreenVisibilityEffect() {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> ChatScreenVisibility.setVisible(true)
                Lifecycle.Event.ON_PAUSE -> ChatScreenVisibility.setVisible(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            ChatScreenVisibility.setVisible(true)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            ChatScreenVisibility.setVisible(false)
        }
    }
}
