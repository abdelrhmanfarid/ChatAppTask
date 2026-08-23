package com.example.chatapptask.core.database.mapper

import com.example.chatapptask.core.database.entity.UserEntity
import com.example.chatapptask.core.domain.model.User

fun UserEntity.toDomain(): User =
    User(
        id = id,
        username = username,
        profileImagePath = profileImagePath,
        age = age,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun User.toEntity(): UserEntity =
    UserEntity(
        id = id,
        username = username,
        profileImagePath = profileImagePath,
        age = age,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
