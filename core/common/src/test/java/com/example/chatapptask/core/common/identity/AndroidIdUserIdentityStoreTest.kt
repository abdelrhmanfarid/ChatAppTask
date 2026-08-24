package com.example.chatapptask.core.common.identity

import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidIdUserIdentityStoreTest {
    @Test
    fun identicalAndroidIds_produceIdenticalUuids() {
        val first = AndroidIdUuid.fromAndroidId("9774d56d682e549c")
        val second = AndroidIdUuid.fromAndroidId("9774D56D682E549C")

        assertEquals(first, second)
        assertEquals(
            UUID.nameUUIDFromBytes("9774d56d682e549c".toByteArray(StandardCharsets.UTF_8)),
            first,
        )
    }

    @Test
    fun differentAndroidIds_produceDifferentUuids() {
        val first = AndroidIdUuid.fromAndroidId("9774d56d682e549c")
        val second = AndroidIdUuid.fromAndroidId("0123456789abcdef")

        assertNotEquals(first, second)
    }

    @Test
    fun store_returnsStableUuid_forSameAndroidId() = runTest {
        val store = AndroidIdUserIdentityStore { "9774d56d682e549c" }

        val first = store.getOrCreateUserId()
        val second = store.getOrCreateUserId()

        assertEquals(first, second)
        assertEquals(AndroidIdUuid.fromAndroidId("9774d56d682e549c"), first)
    }

    @Test
    fun blankAndroidId_failsWithoutGeneratingRandomUuid() = runTest {
        val store = AndroidIdUserIdentityStore { "  " }
        val error = runCatching { store.getOrCreateUserId() }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
    }

    @Test
    fun nullAndroidId_failsWithoutGeneratingRandomUuid() = runTest {
        val store = AndroidIdUserIdentityStore { null }
        val error = runCatching { store.getOrCreateUserId() }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
    }
}
