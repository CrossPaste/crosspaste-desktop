package com.crosspaste.config

import com.crosspaste.presist.OneFilePersist
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getJsonUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath

/**
 * Imports the single JSON document (`appConfig.json`) that earlier versions kept
 * their whole configuration in.
 *
 * Every known key found in the document is written to the store owning it, then
 * the document is renamed to `appConfig.json.migrated` so that DataStore remains
 * the only source of truth. Keys the schema does not know (such as the long-gone
 * `appInstanceId`) and values of the wrong type are skipped. A document that is
 * not valid JSON is moved to `appConfig.json.corrupt` and ignored, as the previous
 * implementation did.
 */
class LegacyJsonConfigImporter(
    private val legacyPersist: OneFilePersist,
) {

    companion object {
        const val FILE_NAME = "appConfig.json"

        const val MIGRATED_SUFFIX = ".migrated"
    }

    private val logger = KotlinLogging.logger {}

    private val fileSystem = getFileUtils().fileSystem

    private val jsonUtils = getJsonUtils()

    val legacyPath: Path = legacyPersist.path

    val archivePath: Path = "${legacyPersist.path}$MIGRATED_SUFFIX".toPath()

    /**
     * Returns true when a legacy document was found and imported.
     *
     * @throws IOException when the document cannot be read, a store cannot be
     *   written, or the document cannot be archived; the next launch retries.
     */
    suspend fun importInto(repository: ConfigRepository): Boolean {
        val content = legacyPersist.readBytes()?.decodeToString() ?: return false
        val document = parse(content) ?: return false

        val imported = mutableListOf<String>()
        for (scope in ConfigScope.entries) {
            val entries =
                repository.schema.keys
                    .filter { it.scope == scope }
                    .mapNotNull { key -> (document[key.name] as? JsonPrimitive)?.let { key to it } }
            if (entries.isEmpty()) {
                continue
            }
            repository.store(scope).edit { preferences ->
                for ((key, primitive) in entries) {
                    if (key.writeJson(preferences, primitive)) {
                        imported += key.name
                    } else {
                        logger.warn { "Skipping legacy config key ${key.name}: cannot read $primitive as its type" }
                    }
                }
            }
        }
        archive()
        logger.info { "Imported ${imported.size} legacy config values from $legacyPath; archived it to $archivePath" }
        return true
    }

    private fun parse(content: String): JsonObject? =
        try {
            jsonUtils.JSON.parseToJsonElement(content) as? JsonObject
                ?: throw SerializationException("Legacy config is not a JSON object")
        } catch (e: SerializationException) {
            val backup = legacyPersist.quarantine()
            logger.error(e) { "Legacy config $legacyPath is corrupt; backed it up to $backup and ignoring it" }
            null
        }

    private fun archive() {
        fileSystem.delete(archivePath, mustExist = false)
        fileSystem.atomicMove(legacyPath, archivePath)
    }
}
