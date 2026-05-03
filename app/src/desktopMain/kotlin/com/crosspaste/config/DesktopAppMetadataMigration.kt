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
    val legacyId =
        runCatching {
            legacyConfigPersist
                .read(LegacyAppInstanceIdHolder::class)
                ?.appInstanceId
                ?.takeIf { it.isNotBlank() }
        }.onFailure {
            logger.error(it) {
                "Failed to read legacy appInstanceId; " +
                    "a new appInstanceId will be generated and this device " +
                    "will appear as a new peer to previously paired devices"
            }
        }.getOrNull() ?: return

    metadataPersist.save(AppMetadata(legacyId))
}
