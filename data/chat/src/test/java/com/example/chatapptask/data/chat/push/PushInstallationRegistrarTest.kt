package com.example.chatapptask.data.chat.push

import com.example.chatapptask.core.common.identity.UserIdentityStore
import com.example.chatapptask.data.chat.remote.PushRegistrationRemoteDataSource
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PushInstallationRegistrarTest {
    @Test
    fun onRegisteredAsync_cachesFidBeforeRemoteRegistration() {
        val ownerId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val store = InMemoryPushInstallationIdStore()
        val remoteStarted = CountDownLatch(1)
        val allowRemote = CountDownLatch(1)
        val fidAtRemoteStart = AtomicReference<String?>()
        val remote = object : PushRegistrationRemoteDataSource {
            override suspend fun registerInstallation(
                ownerId: UUID,
                installationId: String,
            ) {
                fidAtRemoteStart.set(store.get())
                remoteStarted.countDown()
                assertTrue(allowRemote.await(2, TimeUnit.SECONDS))
            }
        }
        val registrar = PushInstallationRegistrar(
            userIdentityStore = FixedUserIdentityStore(ownerId),
            remoteDataSource = remote,
            installationIdStore = store,
            applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )

        registrar.onRegisteredAsync("fid-before-remote")

        assertEquals("fid-before-remote", store.get())
        assertTrue(remoteStarted.await(2, TimeUnit.SECONDS))
        assertEquals("fid-before-remote", fidAtRemoteStart.get())
        allowRemote.countDown()
    }

    @Test
    fun reconcile_withCachedFid_forwardsCurrentIdentityAndFid() = runBlocking {
        val ownerId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val store = InMemoryPushInstallationIdStore().apply { save("fid-cached") }
        val remote = RecordingPushRegistrationRemoteDataSource()
        val registrar = PushInstallationRegistrar(
            userIdentityStore = FixedUserIdentityStore(ownerId),
            remoteDataSource = remote,
            installationIdStore = store,
            applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )

        registrar.reconcile()

        assertEquals(1, remote.calls.size)
        assertEquals(ownerId, remote.calls.single().ownerId)
        assertEquals("fid-cached", remote.calls.single().installationId)
    }

    @Test
    fun reconcile_withNoCachedFid_performsNoRemoteCall() = runBlocking {
        val ownerId = UUID.fromString("44444444-4444-4444-4444-444444444444")
        val remote = RecordingPushRegistrationRemoteDataSource()
        val registrar = PushInstallationRegistrar(
            userIdentityStore = FixedUserIdentityStore(ownerId),
            remoteDataSource = remote,
            installationIdStore = InMemoryPushInstallationIdStore(),
            applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )

        registrar.reconcile()

        assertTrue(remote.calls.isEmpty())
    }

    @Test
    fun remoteFailure_doesNotDeleteCachedFid() = runBlocking {
        val ownerId = UUID.fromString("55555555-5555-5555-5555-555555555555")
        val store = InMemoryPushInstallationIdStore().apply { save("fid-keep") }
        val registrar = PushInstallationRegistrar(
            userIdentityStore = FixedUserIdentityStore(ownerId),
            remoteDataSource = FailingPushRegistrationRemoteDataSource(),
            installationIdStore = store,
            applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )

        try {
            registrar.reconcile()
            fail("Expected IOException")
        } catch (_: IOException) {
            // expected
        }

        assertEquals("fid-keep", store.get())
    }

    @Test
    fun register_propagatesRemoteFailure() = runBlocking {
        val ownerId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val registrar = PushInstallationRegistrar(
            userIdentityStore = FixedUserIdentityStore(ownerId),
            remoteDataSource = FailingPushRegistrationRemoteDataSource(),
            installationIdStore = InMemoryPushInstallationIdStore(),
            applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )

        try {
            registrar.register("fid-xyz")
            fail("Expected IOException")
        } catch (error: IOException) {
            assertTrue(error.message!!.contains("register-push failed"))
        }
    }

    private class FixedUserIdentityStore(
        private val userId: UUID,
    ) : UserIdentityStore {
        override suspend fun getOrCreateUserId(): UUID = userId
    }

    private class InMemoryPushInstallationIdStore : PushInstallationIdStore {
        private var value: String? = null

        override fun save(installationId: String) {
            val trimmed = installationId.trim()
            if (trimmed.isEmpty()) return
            value = trimmed
        }

        override fun get(): String? = value
    }

    private class RecordingPushRegistrationRemoteDataSource : PushRegistrationRemoteDataSource {
        data class Call(
            val ownerId: UUID,
            val installationId: String,
        )

        val calls = mutableListOf<Call>()

        override suspend fun registerInstallation(
            ownerId: UUID,
            installationId: String,
        ) {
            calls += Call(ownerId, installationId)
        }
    }

    private class FailingPushRegistrationRemoteDataSource : PushRegistrationRemoteDataSource {
        override suspend fun registerInstallation(
            ownerId: UUID,
            installationId: String,
        ) {
            throw IOException("register-push failed with HTTP 500")
        }
    }
}
