package com.example.chatapptask.core.common.identity

import java.util.UUID

class AndroidIdUserIdentityStore(
    private val readAndroidId: () -> String?,
) : UserIdentityStore {
    @Volatile
    private var cachedUserId: UUID? = null

    override suspend fun getOrCreateUserId(): UUID {
        cachedUserId?.let { return it }
        val androidId = readAndroidId()?.trim().orEmpty()
        check(androidId.isNotEmpty()) {
            "ANDROID_ID is unavailable; cannot derive a stable device identity."
        }
        return AndroidIdUuid.fromAndroidId(androidId).also { cachedUserId = it }
    }
}
