package com.example.chatapptask.data.chat.repository

import com.example.chatapptask.core.common.identity.UserIdentityStore
import com.example.chatapptask.core.domain.model.User
import com.example.chatapptask.core.domain.repository.UserRepository
import com.example.chatapptask.data.chat.local.ChatLocalDataSource
import com.example.chatapptask.data.chat.remote.ChatRemoteDataSource
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class DefaultUserRepository @Inject constructor(
    private val localDataSource: ChatLocalDataSource,
    private val remoteDataSource: ChatRemoteDataSource,
    private val userIdentityStore: UserIdentityStore,
) : UserRepository {
    override suspend fun getCurrentUserId(): UUID =
        userIdentityStore.getOrCreateUserId()

    override suspend fun getUser(userId: UUID): User? {
        localDataSource.getUserById(userId)?.let { user -> return user }
        return remoteDataSource.getUser(userId)?.also { user ->
            localDataSource.upsertUser(user)
        }
    }

    override fun observeUser(userId: UUID): Flow<User?> =
        localDataSource.observeUserById(userId)

    override suspend fun upsertUser(user: User) {
        val savedUser = remoteDataSource.upsertUser(user)
        localDataSource.upsertUser(savedUser)
    }

    override suspend fun uploadProfileImage(
        bytes: ByteArray,
        mimeType: String,
        fileExtension: String,
    ): String {
        val userId = userIdentityStore.getOrCreateUserId()
        return remoteDataSource.uploadProfileImage(
            userId = userId,
            bytes = bytes,
            mimeType = mimeType,
            fileExtension = fileExtension,
        )
    }
}
