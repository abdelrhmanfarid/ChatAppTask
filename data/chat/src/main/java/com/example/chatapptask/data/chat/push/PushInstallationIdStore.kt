package com.example.chatapptask.data.chat.push

/**
 * Private app-local cache of the latest Firebase Installation ID for push registration.
 */
interface PushInstallationIdStore {
    fun save(installationId: String)

    fun get(): String?
}
