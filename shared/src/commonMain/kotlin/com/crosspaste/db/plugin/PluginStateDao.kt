package com.crosspaste.db.plugin

import com.crosspaste.Database
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.withContext

data class PluginState(
    val id: String,
    val version: String,
    val enabled: Boolean,
    val source: String,
    val installedAt: Long,
) {
    companion object {
        const val SOURCE_LOCAL = "local"
    }
}

class PluginStateDao(
    database: Database,
) {

    private val pluginDatabaseQueries = database.pluginDatabaseQueries

    suspend fun getAll(): List<PluginState> =
        withContext(ioDispatcher) {
            pluginDatabaseQueries
                .getAllPluginStates { id, version, enabled, source, installedAt ->
                    PluginState(id, version, enabled, source, installedAt)
                }.executeAsList()
        }

    /** Inserts the state, or updates version and enabled while keeping the original source and installedAt. */
    suspend fun upsert(state: PluginState): Unit =
        withContext(ioDispatcher) {
            pluginDatabaseQueries.upsertPluginState(
                id = state.id,
                version = state.version,
                enabled = state.enabled,
                source = state.source,
                installedAt = state.installedAt,
            )
        }
}
