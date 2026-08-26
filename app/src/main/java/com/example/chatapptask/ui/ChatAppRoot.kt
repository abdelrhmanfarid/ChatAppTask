package com.example.chatapptask.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chatapptask.feature.chat.presentation.ChatRoute
import com.example.chatapptask.feature.profile.presentation.ProfileSetupRoute
import com.example.chatapptask.ui.startup.StartupDestination
import com.example.chatapptask.ui.startup.StartupErrorScreen
import com.example.chatapptask.ui.startup.StartupResolvingScreen
import com.example.chatapptask.ui.startup.StartupViewModel

@Composable
fun ChatAppRoot(
    viewModel: StartupViewModel,
    modifier: Modifier = Modifier,
) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    when (destination) {
        StartupDestination.Resolving -> StartupResolvingScreen(modifier)
        StartupDestination.ProfileSetup -> ProfileSetupRoute(
            onProfileSaved = viewModel::onProfileSaved,
            modifier = modifier,
        )
        StartupDestination.Chat -> {
            ChatScreenVisibilityEffect()
            MessageSendNotificationPermissionEffect()
            ChatRoute(modifier = modifier)
        }
        StartupDestination.Error -> StartupErrorScreen(
            onRetry = viewModel::retry,
            modifier = modifier,
        )
    }
}
