package com.example.chatapptask.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterPushRequestDto(
    @SerialName("ownerId")
    val ownerId: String,
    @SerialName("installationId")
    val installationId: String,
)
