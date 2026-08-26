package com.example.chatapptask.data.chat.push

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedPreferencesPushInstallationIdStore @Inject constructor(
    @ApplicationContext context: Context,
) : PushInstallationIdStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun save(installationId: String) {
        val trimmed = installationId.trim()
        if (trimmed.isEmpty()) return
        prefs.edit().putString(KEY_INSTALLATION_ID, trimmed).apply()
    }

    override fun get(): String? =
        prefs.getString(KEY_INSTALLATION_ID, null)?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val PREFS_NAME = "chat_app_push_registration"
        const val KEY_INSTALLATION_ID = "installation_id"
    }
}
