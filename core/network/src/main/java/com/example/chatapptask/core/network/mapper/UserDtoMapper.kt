package com.example.chatapptask.core.network.mapper

import com.example.chatapptask.core.domain.model.User
import com.example.chatapptask.core.network.dto.UserDto
import com.example.chatapptask.core.network.dto.UserUpsertDto
import java.time.Instant
import java.util.UUID

fun UserDto.toDomain(): User =
    User(
        id = UUID.fromString(id),
        username = username,
        profileImagePath = profileImagePath,
        age = age,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
    )

fun User.toUpsertDto(): UserUpsertDto =
    UserUpsertDto(
        id = id.toString(),
        username = username,
        profileImagePath = profileImagePath,
        age = age,
    )
