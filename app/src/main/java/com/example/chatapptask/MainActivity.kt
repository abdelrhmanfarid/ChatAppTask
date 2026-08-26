package com.example.chatapptask

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.chatapptask.fcm.ChatNotificationIntents
import com.example.chatapptask.ui.ChatAppRoot
import com.example.chatapptask.ui.startup.StartupDestination
import com.example.chatapptask.ui.startup.StartupViewModel
import com.example.chatapptask.ui.theme.ChatAppTaskTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val startupViewModel: StartupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition {
            startupViewModel.destination.value is StartupDestination.Resolving
        }
        enableEdgeToEdge()
        handleChatNotificationIntent(intent)
        setContent {
            ChatAppTaskTheme {
                ChatAppRoot(viewModel = startupViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleChatNotificationIntent(intent)
    }

    /**
     * Consumes OPEN_CHAT notification taps without bypassing startup/profile resolution.
     * Destination remains owned by [StartupViewModel]; message_id is carried for future use.
     */
    private fun handleChatNotificationIntent(intent: Intent?) {
        ChatNotificationIntents.consumeOpenChatMessageId(intent)
        // No forced navigation: cold start still resolves profile first, then Chat when ready.
    }
}
