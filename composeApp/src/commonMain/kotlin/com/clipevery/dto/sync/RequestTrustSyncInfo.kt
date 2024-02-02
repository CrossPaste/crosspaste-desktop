package com.clipevery.dto.sync

import com.clipevery.serializer.IdentityKeySerializer
import kotlinx.serialization.Serializable
import org.signal.libsignal.protocol.IdentityKey

@Serializable
data class RequestTrustSyncInfo(
    @Serializable(with = IdentityKeySerializer::class) val identityKey: IdentityKey,
    val syncInfo: SyncInfo
)

