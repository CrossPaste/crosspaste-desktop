package com.crosspaste.config

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Which preferences file a [ConfigKey] is stored in.
 *
 * User preferences and transient runtime state live in separate files so that a
 * bookkeeping write (the pasteboard change count saved on every exit, a dismissed
 * prompt, a restore token) never rewrites the file holding the user's settings.
 */
enum class ConfigScope(
    val fileName: String,
) {
    /** Settings the user chose: port, storage path, language, sync toggles, ... */
    SETTINGS("appConfig.preferences_pb"),

    /** State the app records for itself; losing it is an inconvenience, never a lost setting. */
    RUNTIME_STATE("appState.preferences_pb"),
}

/**
 * A typed configuration key shared by every platform.
 *
 * [name] is the identifier callers pass to [ConfigManager.updateConfig] and the
 * property name in the legacy `appConfig.json`; [preferencesKey] is the DataStore
 * key the value is stored under. Values reach the generic update API loosely typed
 * (`Any`), so each key knows how to [coerce] them, mirroring the conversions in
 * [AppConfig.Companion].
 */
class ConfigKey<T : Any> internal constructor(
    val name: String,
    val scope: ConfigScope,
    val preferencesKey: Preferences.Key<T>,
    private val coerce: (Any) -> T,
    private val fromJson: (JsonPrimitive) -> T?,
) {

    /** The stored value, or null when the key has never been written. */
    fun read(preferences: Preferences): T? = preferences[preferencesKey]

    /** Stores [value] after coercing it to the key's type. */
    fun write(
        preferences: MutablePreferences,
        value: Any,
    ) {
        preferences[preferencesKey] = coerce(value)
    }

    /**
     * Imports a legacy JSON value, returning false when it cannot be read as this
     * key's type; the stored value is then left untouched.
     */
    fun writeJson(
        preferences: MutablePreferences,
        primitive: JsonPrimitive,
    ): Boolean {
        val value = fromJson(primitive) ?: return false
        preferences[preferencesKey] = value
        return true
    }

    override fun toString(): String = name
}

/** The stored value of [key], or null when it has never been written. */
operator fun <T : Any> Preferences.get(key: ConfigKey<T>): T? = key.read(this)

fun booleanConfigKey(
    name: String,
    scope: ConfigScope = ConfigScope.SETTINGS,
): ConfigKey<Boolean> =
    ConfigKey(
        name = name,
        scope = scope,
        preferencesKey = booleanPreferencesKey(name),
        coerce = { AppConfig.toBoolean(it) },
        fromJson = { it.booleanOrNull },
    )

fun intConfigKey(
    name: String,
    scope: ConfigScope = ConfigScope.SETTINGS,
): ConfigKey<Int> =
    ConfigKey(
        name = name,
        scope = scope,
        preferencesKey = intPreferencesKey(name),
        coerce = { AppConfig.toInt(it) },
        fromJson = { it.intOrNull },
    )

fun longConfigKey(
    name: String,
    scope: ConfigScope = ConfigScope.SETTINGS,
): ConfigKey<Long> =
    ConfigKey(
        name = name,
        scope = scope,
        preferencesKey = longPreferencesKey(name),
        coerce = { AppConfig.toLong(it) },
        fromJson = { it.longOrNull },
    )

fun stringConfigKey(
    name: String,
    scope: ConfigScope = ConfigScope.SETTINGS,
): ConfigKey<String> =
    ConfigKey(
        name = name,
        scope = scope,
        preferencesKey = stringPreferencesKey(name),
        coerce = { AppConfig.toString(it) },
        fromJson = { if (it is JsonNull) null else it.content },
    )
