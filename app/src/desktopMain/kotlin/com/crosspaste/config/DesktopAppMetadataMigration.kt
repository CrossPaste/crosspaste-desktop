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
    // Presence check, not a read: if a metadata file exists at all — even unreadable —
    // never overwrite it here. AppMetadataRepository owns the corrupt/IO-error handling
    // (quarantine or fail startup, R2-02-006); silently replacing the file on a read
    // error would rotate the device identity.
    if (metadataPersist.exists()) return
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
