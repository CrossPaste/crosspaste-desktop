package com.crosspaste.config

import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Guards the three places a desktop setting is declared against drifting apart:
 * the [DesktopAppConfig] property, its [ConfigKey] and the line in
 * [DesktopAppConfig.fromPreferences] that reads it.
 */
class DesktopConfigKeysTest {

    private val json = Json { encodeDefaults = true }

    private val defaults = DesktopAppConfig(language = "en")

    private fun fields(config: DesktopAppConfig): Map<String, JsonPrimitive> =
        json
            .encodeToJsonElement(DesktopAppConfig.serializer(), config)
            .jsonObject
            .mapValues { (_, value) -> value.jsonPrimitive }

    @Test
    fun `schema declares exactly the serialized config properties`() {
        assertEquals(
            fields(defaults).keys,
            DesktopConfigKeys.schema.keys
                .map { it.name }
                .toSet(),
        )
    }

    @Test
    fun `every key is read back by fromPreferences`() {
        val before = fields(defaults)
        val preferences = mutablePreferencesOf()
        for (key in DesktopConfigKeys.schema.keys) {
            // A value that differs from the default whatever the key's type; write()
            // coerces it (a Long lands in an Int key as its Int value).
            val current = before.getValue(key.name).content
            val changed: Any =
                current.toBooleanStrictOrNull()?.let { !it }
                    ?: current.toLongOrNull()?.let { it + 1 }
                    ?: (current + "-changed")
            key.write(preferences, changed)
        }

        val after = fields(DesktopAppConfig.fromPreferences(preferences, defaults))

        for (name in before.keys) {
            assertNotEquals(before.getValue(name), after.getValue(name), "fromPreferences ignores $name")
        }
    }

    @Test
    fun `copy by key name changes only that property`() {
        val updated = defaults.copy("port", 4321)
        val changed = fields(defaults).filter { (name, value) -> fields(updated).getValue(name) != value }.keys
        assertEquals(setOf("port"), changed)
    }

    @Test
    fun `bookkeeping keys live in the runtime state scope`() {
        val runtimeState =
            DesktopConfigKeys.schema.keys
                .filter { it.scope == ConfigScope.RUNTIME_STATE }
                .map { it.name }
        assertEquals(
            setOf(
                "lastPasteboardChangeCount",
                "networkBlockingDismissedFingerprint",
                "lastSeenChangelogVersion",
                "linuxRemoteDesktopRestoreToken",
            ),
            runtimeState.toSet(),
        )
        assertTrue(DesktopConfigKeys.schema.key("port").scope == ConfigScope.SETTINGS)
    }
}
