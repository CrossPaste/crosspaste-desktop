package com.crosspaste.config

import com.crosspaste.presist.OneFilePersist
import com.crosspaste.utils.DeviceUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerializationException

class AppMetadataRepository(
    private val persist: OneFilePersist,
    private val deviceUtils: DeviceUtils,
) {
    private val logger = KotlinLogging.logger {}

    val appInstanceId: String by lazy { loadOrCreate().appInstanceId }

    /**
     * The metadata file is this device's persistent identity: silently replacing it
     * makes every previously paired device see a brand-new peer, orphaning existing
     * trust and sync state (R2-02-006). Only a genuinely absent file may create a
     * fresh identity:
     * - Corrupt content is quarantined next to the original (observable in logs,
     *   recoverable by hand) before a new identity is generated.
     * - I/O errors (permissions, transient disk failures) propagate and fail startup
     *   rather than rotating the identity.
     */
    private fun loadOrCreate(): AppMetadata {
        val existing =
            try {
                persist.read(AppMetadata::class)
            } catch (e: SerializationException) {
                // Corrupt content, not an I/O failure. SerializationException covers all
                // kotlinx parse failures; anything broader would misclassify unrelated
                // errors as corruption and rotate the identity.
                val backupPath = persist.quarantine()
                logger.error(e) {
                    "App metadata is corrupt; backed it up to $backupPath and generating a new " +
                        "appInstanceId. Previously paired devices will see this device as a new peer."
                }
                null
            }
        return existing ?: AppMetadata(deviceUtils.createAppInstanceId()).also { persist.save(it) }
    }
}
