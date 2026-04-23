package com.crosspaste.bootstrap

import com.crosspaste.presist.OneFilePersist
import java.io.ByteArrayInputStream
import java.util.Properties

/**
 * Loads optional JVM system-property overrides from a plain `.properties` file in the user's
 * CrossPaste config directory and applies them via [System.setProperty] BEFORE any Compose
 * or skiko class is loaded.
 *
 * File location is owned by the caller (resolved through `AppPathProvider`); only keys matching
 * [ALLOWED_PREFIXES] are applied, everything else is logged and skipped.
 *
 * Must run before configManager / logger / Compose / skiko / AWT classes initialize. The
 * caller is responsible for ordering this in the companion-object init sequence.
 */
object JvmSystemPropertiesOverride {

    const val FILE_NAME = "jvm-system-properties.properties"

    private const val LOG_PREFIX = "[CrossPasteBootstrap]"

    private val ALLOWED_PREFIXES =
        listOf(
            "skiko.",
            "compose.",
            "sun.java2d.",
            "sun.awt.",
            "awt.",
            "swing.",
            "crosspaste.",
        )

    fun apply(persist: OneFilePersist) {
        val bytes =
            runCatching { persist.readBytes() }.getOrElse { e ->
                System.err.println("$LOG_PREFIX failed to read ${persist.path}: ${e.message}")
                return
            } ?: return

        val props =
            runCatching {
                Properties().apply { ByteArrayInputStream(bytes).use { load(it) } }
            }.getOrElse { e ->
                System.err.println("$LOG_PREFIX failed to parse ${persist.path}: ${e.message}")
                return
            }

        var applied = 0
        var rejected = 0
        for (name in props.stringPropertyNames()) {
            val value = props.getProperty(name) ?: continue
            if (!isAllowed(name)) {
                System.err.println("$LOG_PREFIX reject $name (not in allowlist)")
                rejected++
                continue
            }
            System.setProperty(name, value)
            System.err.println("$LOG_PREFIX apply $name=$value")
            applied++
        }
        System.err.println(
            "$LOG_PREFIX loaded ${persist.path}: applied=$applied, rejected=$rejected",
        )
    }

    internal fun isAllowed(key: String): Boolean = ALLOWED_PREFIXES.any { key.startsWith(it) }
}
