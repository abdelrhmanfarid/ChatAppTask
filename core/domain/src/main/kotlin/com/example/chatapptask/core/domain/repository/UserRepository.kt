package com.example.chatapptask.core.domain.repository

import com.example.chatapptask.core.domain.model.User
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun getCurrentUserId(): UUID

    suspend fun getUser(userId: UUID): User?

    fun observeUser(userId: UUID): Flow<User?>

    suspend fun upsertUser(user: User)
}
