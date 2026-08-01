package com.crosspaste.dto.sync

import com.crosspaste.serializer.Base64ByteArraySerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ControlChallenge(
    @Serializable(with = Base64ByteArraySerializer::class)
    val id: ByteArray,
    @Serializable(with = Base64ByteArraySerializer::class)
    val nonce: ByteArray,
)

@Serializable
enum class ControlOperation {
    @SerialName("notify_exit")
    NOTIFY_EXIT,

    @SerialName("notify_remove")
    NOTIFY_REMOVE,
}

@Serializable
data class AuthenticatedControlRequest(
    @Serializable(with = Base64ByteArraySerializer::class)
    val challengeId: ByteArray,
    @Serializable(with = Base64ByteArraySerializer::class)
    val challengeNonce: ByteArray,
    val targetAppInstanceId: String,
    val operation: ControlOperation,
)
