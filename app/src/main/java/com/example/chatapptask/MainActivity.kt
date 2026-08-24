package com.example.chatapptask

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
        setContent {
            ChatAppTaskTheme {
                ChatAppRoot(viewModel = startupViewModel)
            }
        }
    }
}
