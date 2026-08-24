package com.example.chatapptask

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.chatapptask.ui.ChatAppRoot
import com.example.chatapptask.ui.startup.StartupViewModel
import com.example.chatapptask.ui.theme.ChatAppTaskTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val startupViewModel: StartupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChatAppTaskTheme {
                ChatAppRoot(viewModel = startupViewModel)
            }
        }
    }
}
