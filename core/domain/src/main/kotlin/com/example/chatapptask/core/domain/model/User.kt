package com.example.chatapptask.core.domain.model

import java.time.Instant
import java.util.UUID

data class User(
    val id: UUID,
    val username: String,
    val profileImagePath: String?,
    val age: Int?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
