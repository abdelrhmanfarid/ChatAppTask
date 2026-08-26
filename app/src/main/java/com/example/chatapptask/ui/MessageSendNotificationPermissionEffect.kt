package com.example.chatapptask.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
internal fun MessageSendNotificationPermissionEffect() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Sending continues whether the permission is granted or denied. */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return@LaunchedEffect
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(REQUESTED_KEY, false)) return@LaunchedEffect
        prefs.edit().putBoolean(REQUESTED_KEY, true).apply()
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private const val PREFS_NAME = "chat_app_notifications"
private const val REQUESTED_KEY = "post_notifications_requested"
