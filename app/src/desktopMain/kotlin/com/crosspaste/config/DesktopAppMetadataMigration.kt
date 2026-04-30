package com.crosspaste.config

import com.crosspaste.presist.OneFilePersist
import kotlinx.serialization.Serializable

@Serializable
private data class LegacyAppInstanceIdHolder(
    val appInstanceId: String = "",
)

fun migrateAppInstanceIdIfNeeded(
    metadataPersist: OneFilePersist,
    legacyConfigPersist: OneFilePersist,
) {
    if (metadataPersist.read(AppMetadata::class) != null) return
    runCatching {
        legacyConfigPersist
            .read(LegacyAppInstanceIdHolder::class)
            ?.appInstanceId
            ?.takeIf { it.isNotBlank() }
            ?.let { metadataPersist.save(AppMetadata(it)) }
    }
}
