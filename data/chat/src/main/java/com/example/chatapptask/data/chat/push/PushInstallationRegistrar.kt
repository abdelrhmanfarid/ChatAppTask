package com.example.chatapptask.data.chat.push

import com.example.chatapptask.core.common.identity.UserIdentityStore
import com.example.chatapptask.data.chat.di.ApplicationScope
import com.example.chatapptask.data.chat.remote.PushRegistrationRemoteDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Uploads Firebase Installation IDs to Supabase via [PushRegistrationRemoteDataSource].
 * Caches the latest FID locally and reconciles after the user/profile is known to exist.
 * Does not touch Room, notifications, or message sync.
 */
@Singleton
class PushInstallationRegistrar @Inject constructor(
    private val userIdentityStore: UserIdentityStore,
    private val remoteDataSource: PushRegistrationRemoteDataSource,
    private val installationIdStore: PushInstallationIdStore,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    /**
     * FCM `onRegistered` entry: persist FID, then attempt registration asynchronously.
     */
    fun onRegisteredAsync(installationId: String) {
        val trimmed = installationId.trim()
        if (trimmed.isEmpty()) return
        installationIdStore.save(trimmed)
        applicationScope.launch {
            runCatching { register(trimmed) }
        }
    }

    /**
     * After startup confirms the current user/profile exists, retry any cached FID.
     */
    fun reconcileAsync() {
        applicationScope.launch {
            runCatching { reconcile() }
        }
    }

    /**
     * Reads the cached FID (if any) and registers. No-op when cache is empty.
     * Propagates remote errors for tests/callers.
     */
    suspend fun reconcile() {
        val installationId = installationIdStore.get() ?: return
        register(installationId)
    }

    /**
     * Registers [installationId] with the current deterministic owner id.
     * Does not modify the FID cache. Propagates remote errors.
     */
    suspend fun register(installationId: String) {
        val ownerId = userIdentityStore.getOrCreateUserId()
        remoteDataSource.registerInstallation(
            ownerId = ownerId,
            installationId = installationId,
        )
    }
}
