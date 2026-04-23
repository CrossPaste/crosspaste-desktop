package com.crosspaste.bootstrap

import java.io.File
import java.io.FileInputStream
import java.util.Properties

/**
 * Loads optional JVM system-property overrides from a plain `.properties` file in the user's
 * CrossPaste config directory and applies them via [System.setProperty] BEFORE any Compose
 * or skiko class is loaded.
 *
 * Location (mirrors [com.crosspaste.path.DesktopAppPathProvider] USER dir):
 *   Windows: %USERPROFILE%\.crosspaste\jvm-system-properties.properties
 *   Linux:   ~/.local/share/.crosspaste/jvm-system-properties.properties
 *   macOS:   ~/Library/Application Support/CrossPaste/jvm-system-properties.properties
 *
 * Only keys matching [ALLOWED_PREFIXES] are applied; everything else is logged and skipped.
 *
 * Intentionally dependency-free: only JDK APIs. Do NOT add imports from Compose, Koin, Okio,
 * or any CrossPaste service — this class runs before those subsystems exist.
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

    fun apply() {
        val file = resolveOverrideFile() ?: return
        if (!file.isFile) {
            return
        }
        val props =
            runCatching { loadProperties(file) }.getOrElse { e ->
                System.err.println("$LOG_PREFIX failed to read ${file.absolutePath}: ${e.message}")
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
            "$LOG_PREFIX loaded ${file.absolutePath}: applied=$applied, rejected=$rejected",
        )
    }

    internal fun resolveOverrideFile(
        osName: String = System.getProperty("os.name").orEmpty(),
        userHome: String = System.getProperty("user.home").orEmpty(),
    ): File? {
        if (userHome.isEmpty()) return null
        val lower = osName.lowercase()
        val dir =
            when {
                lower.contains("mac") || lower.contains("darwin") ->
                    File(File(File(userHome, "Library"), "Application Support"), "CrossPaste")
                lower.contains("win") -> File(userHome, ".crosspaste")
                else -> File(File(File(userHome, ".local"), "share"), ".crosspaste")
            }
        return File(dir, FILE_NAME)
    }

    internal fun isAllowed(key: String): Boolean = ALLOWED_PREFIXES.any { key.startsWith(it) }

    private fun loadProperties(file: File): Properties {
        val props = Properties()
        FileInputStream(file).use { props.load(it) }
        return props
    }
}
