package com.crosspaste.config

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import okio.IOException

/**
 * The configuration of one platform: its [schema] plus one [ConfigStore] per
 * [ConfigScope]. Each key is written to the store owning it, so a settings
 * change and a runtime-state change never touch each other's file.
 */
class ConfigRepository(
    val schema: ConfigSchema,
    private val stores: Map<ConfigScope, ConfigStore>,
) {

    init {
        val missing = ConfigScope.entries.filter { it !in stores }
        require(missing.isEmpty()) { "No config store for $missing" }
    }

    fun store(scope: ConfigScope): ConfigStore = stores.getValue(scope)

    /**
     * Every stored value across all scopes, merged into one read-only view.
     *
     * @throws IOException when a store cannot be read.
     */
    suspend fun load(): Preferences {
        val merged = mutablePreferencesOf()
        for (scope in ConfigScope.entries) {
            merged += store(scope).snapshot()
        }
        return merged.toPreferences()
    }

    /**
     * Writes each change to the store owning its key, one transaction per store,
     * and returns the merged content afterwards. Every key is resolved before
     * anything is written, so an unknown key leaves all stores untouched.
     *
     * @throws IllegalArgumentException for an unknown key or lists of different sizes.
     * @throws IOException when a store cannot be written; a store edited earlier in
     *   the same call keeps its changes.
     */
    suspend fun update(
        keys: List<String>,
        values: List<Any>,
    ): Preferences {
        require(keys.size == values.size) {
            "keys (${keys.size}) and values (${values.size}) must have the same size"
        }
        val changes = keys.indices.map { i -> schema.key(keys[i]) to values[i] }
        for (scope in ConfigScope.entries) {
            val scoped = changes.filter { (key, _) -> key.scope == scope }
            if (scoped.isEmpty()) {
                continue
            }
            store(scope).edit { preferences ->
                scoped.forEach { (key, value) -> key.write(preferences, value) }
            }
        }
        return load()
    }
}
