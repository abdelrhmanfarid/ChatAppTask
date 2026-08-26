package com.example.chatapptask.core.common.identity

import java.nio.charset.StandardCharsets
import java.util.UUID

internal object AndroidIdUuid {
    fun fromAndroidId(androidId: String): UUID {
        val normalized = androidId.trim().lowercase()
        require(normalized.isNotEmpty()) { "ANDROID_ID must not be blank." }
        return UUID.nameUUIDFromBytes(normalized.toByteArray(StandardCharsets.UTF_8))
    }
}
