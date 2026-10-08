package com.crosspaste.config

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.crosspaste.utils.createPlatformLock
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking

/**
 * A [ConfigManager] persisting through a [ConfigRepository].
 *
 * The in-memory [config] is always a snapshot of what is on disk: an update is
 * written through DataStore first and becomes visible once that succeeded, so
 * observers never see a value that failed to persist, and a failed write leaves
 * both the files and the snapshot as they were. Updates are serialised with a
 * lock, matching the single-writer model of the file-based implementation.
 *
 * On construction, a legacy JSON document is imported when [legacyImporter] finds
 * one, then the stores are read. A store that cannot be read (file locked,
 * permission denied) only costs this session its values: the snapshot starts from
 * [createConfig] defaults, while every later write re-reads the file and changes
 * nothing but its own keys, so the user's file is never overwritten with defaults.
 * A corrupt store has already been quarantined by [ConfigStore] at that point.
 */
abstract class DataStoreConfigManager<T : AppConfig>(
    private val repository: ConfigRepository,
    legacyImporter: LegacyJsonConfigImporter?,
    private val createConfig: (Preferences) -> T,
    private val scope: CoroutineScope,
) : ConfigManager<T> {

    private val logger = KotlinLogging.logger {}

    private val lock = createPlatformLock()

    private val _config: MutableStateFlow<T> = MutableStateFlow(loadInitialConfig(legacyImporter))

    override val config: StateFlow<T> = _config

    private fun loadInitialConfig(legacyImporter: LegacyJsonConfigImporter?): T =
        runBlocking {
            legacyImporter?.let { importer ->
                runCatching { importer.importInto(repository) }
                    .onFailure { e ->
                        logger.error(e) { "Failed to import the legacy config; it stays in place for the next launch" }
                    }
            }
            try {
                createConfig(repository.load())
            } catch (e: Exception) {
                logger.error(e) { "Failed to read the config; using defaults for this session" }
                createConfig(emptyPreferences())
            }
        }

    /** Re-reads the stores; unlike [config] this never falls back to defaults. */
    override fun loadConfig(): T? = runBlocking { createConfig(repository.load()) }

    override fun updateConfig(
        key: String,
        value: Any,
    ) {
        updateConfig(listOf(key), listOf(value))
    }

    /**
     * Persists the changes, then publishes the new snapshot. A write failure is
     * logged and reported through [onSaveFailure], leaving the config unchanged.
     *
     * @throws IllegalArgumentException for an unknown key or lists of different sizes.
     */
    override fun updateConfig(
        keys: List<String>,
        values: List<Any>,
    ) {
        lock.withLock {
            try {
                persist(keys, values)
            } catch (e: IllegalArgumentException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Failed to save config change to $keys" }
                onSaveFailure(e)
            }
        }
    }

    /**
     * Like [updateConfig], but for a change the caller must know reached disk
     * (e.g. a storage migration about to delete the old data): a write failure is
     * thrown instead of only reported, and the config is left unchanged.
     */
    fun updateConfigDurably(
        keys: List<String>,
        values: List<Any>,
    ) {
        lock.withLock {
            persist(keys, values)
        }
    }

    private fun persist(
        keys: List<String>,
        values: List<Any>,
    ) {
        val merged = runBlocking { repository.update(keys, values) }
        _config.value = createConfig(merged)
    }

    /** Called with the update lock held after a write failed; the config is unchanged. */
    protected open fun onSaveFailure(error: Throwable) {}

    /**
     * Releases the underlying DataStores. Only tests that reopen the same files
     * need this: writes complete before [updateConfig] returns, so there is
     * nothing to flush at shutdown.
     */
    fun close() {
        scope.cancel()
        runBlocking { scope.coroutineContext.job.join() }
    }
}
