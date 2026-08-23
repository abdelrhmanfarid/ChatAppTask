package com.example.chatapptask.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    @SerialName("id")
    val id: String,
    @SerialName("username")
    val username: String,
    @SerialName("profile_image_path")
    val profileImagePath: String?,
    @SerialName("age")
    val age: Int?,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
)

@Serializable
data class UserUpsertDto(
    @SerialName("id")
    val id: String,
    @SerialName("username")
    val username: String,
    @SerialName("profile_image_path")
    val profileImagePath: String?,
    @SerialName("age")
    val age: Int?,
)
