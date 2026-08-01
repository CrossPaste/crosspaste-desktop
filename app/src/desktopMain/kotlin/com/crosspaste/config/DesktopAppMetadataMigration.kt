package com.crosspaste.config

import com.crosspaste.presist.OneFilePersist
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

private val logger = KotlinLogging.logger {}

@Serializable
private data class LegacyAppInstanceIdHolder(
    val appInstanceId: String = "",
)

fun migrateAppInstanceIdIfNeeded(
    metadataPersist: OneFilePersist,
    legacyConfigPersist: OneFilePersist,
) {
    if (metadataPersist.exists()) {
        // A metadata file is present: if it parses, identity is settled. If it is
        // corrupt, the legacy config may still hold the original identity — restoring
        // it here beats letting AppMetadataRepository quarantine the file and mint a
        // brand-new id, which would orphan existing pairings (R2-02-006). I/O errors
        // propagate untouched and fail startup: overwriting on a transient read error
        // could revert a newer identity to the legacy one.
        try {
            metadataPersist.read(AppMetadata::class)
        } catch (e: SerializationException) {
            val legacyId = readLegacyAppInstanceId(legacyConfigPersist) ?: return
            val backup = metadataPersist.quarantine()
            logger.error(e) {
                "App metadata is corrupt; backed it up to $backup " +
                    "and restored the legacy appInstanceId"
            }
            metadataPersist.save(AppMetadata(legacyId))
        }
        return
    }
    val legacyId = readLegacyAppInstanceId(legacyConfigPersist) ?: return
    metadataPersist.save(AppMetadata(legacyId))
}

private fun readLegacyAppInstanceId(legacyConfigPersist: OneFilePersist): String? =
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
    }.getOrNull()
