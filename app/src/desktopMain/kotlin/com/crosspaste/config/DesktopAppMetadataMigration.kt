package com.crosspaste.config

import com.crosspaste.presist.OneFilePersist
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable

private val logger = KotlinLogging.logger {}

@Serializable
private data class LegacyAppInstanceIdHolder(
    val appInstanceId: String = "",
)

fun migrateAppInstanceIdIfNeeded(
    metadataPersist: OneFilePersist,
    legacyConfigPersist: OneFilePersist,
) {
    if (runCatching { metadataPersist.read(AppMetadata::class) }.getOrNull() != null) return
    runCatching {
        legacyConfigPersist
            .read(LegacyAppInstanceIdHolder::class)
            ?.appInstanceId
            ?.takeIf { it.isNotBlank() }
            ?.let { metadataPersist.save(AppMetadata(it)) }
    }.onFailure {
        logger.error(it) {
            "Failed to migrate appInstanceId from legacy config; " +
                "a new appInstanceId will be generated and this device " +
                "will appear as a new peer to previously paired devices"
        }
    }
}
