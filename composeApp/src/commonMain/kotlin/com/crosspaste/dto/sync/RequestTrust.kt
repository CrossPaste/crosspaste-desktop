package com.crosspaste.dto.sync

import com.crosspaste.serializer.IdentityKeySerializer
import kotlinx.serialization.Serializable
import org.signal.libsignal.protocol.IdentityKey

@Serializable
data class RequestTrust(
    @Serializable(with = IdentityKeySerializer::class) val identityKey: IdentityKey,
    val token: Int,
)
