package com.example.chatapptask.core.common.identity

import java.util.UUID

interface UserIdentityStore {
    suspend fun getOrCreateUserId(): UUID
}
