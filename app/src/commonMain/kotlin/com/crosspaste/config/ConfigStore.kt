package com.crosspaste.config

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.crosspaste.presist.OneFilePersist
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import okio.IOException
import okio.Path

/**
 * One preferences file backed by Jetpack DataStore.
 *
 * DataStore gives every write transaction semantics (read-modify-write under a
 * lock, written to a scratch file and atomically renamed into place), so a crash
 * can never leave a half-written file behind, and exposes the content as a [Flow].
 *
 * A file DataStore cannot parse is moved aside to `<name>.corrupt` before the
 * store continues from empty preferences, so an unreadable payload stays
 * available for inspection instead of being overwritten by defaults. When even
 * that move fails, the corruption is surfaced to the caller and nothing is
 * written: a file that could be neither read nor preserved must not be replaced.
 *
 * DataStore allows one active instance per file: create a single store per file
 * per process, and (in tests) [close] it before opening the same file again.
 */
class ConfigStore(
    val path: Path,
    scope: CoroutineScope,
) {

    private val logger = KotlinLogging.logger {}

    private val dataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(
            corruptionHandler =
                ReplaceFileCorruptionHandler<Preferences> { corruption ->
                    replaceCorruptFile(corruption)
                },
            scope = scope,
        ) { path }

    val data: Flow<Preferences>
        get() = dataStore.data

    /**
     * The current content. The first call reads the file; later calls return the
     * in-memory copy DataStore keeps in sync with its own writes.
     *
     * @throws IOException when the file cannot be read, or is corrupt and could not be quarantined.
     */
    suspend fun snapshot(): Preferences = dataStore.data.first()

    /**
     * Applies [transform] to the current content as one transaction and returns
     * the resulting content.
     *
     * @throws IOException when the file cannot be read or written; it is then unchanged.
     */
    suspend fun edit(transform: (MutablePreferences) -> Unit): Preferences = dataStore.edit { transform(it) }

    private fun replaceCorruptFile(corruption: CorruptionException): Preferences {
        val backup =
            try {
                OneFilePersist(path).quarantine()
            } catch (e: IOException) {
                logger.error(e) { "Config file $path is corrupt and could not be backed up; leaving it untouched" }
                throw e
            }
        logger.error(corruption) { "Config file $path is corrupt; backed it up to $backup and starting from defaults" }
        return emptyPreferences()
    }
}
