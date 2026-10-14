package com.crosspaste.plugin

import com.crosspaste.platform.Platform
import java.io.File

/**
 * Directories an app launched from Finder or the Dock lacks on its PATH but
 * where users install the tools plugins need (Homebrew). Requirement checks
 * and the plugin process both use them, so they agree on what is available.
 */
fun pluginExtraPathDirs(platform: Platform): List<String> =
    if (platform.isMacos()) {
        listOf("/opt/homebrew/bin", "/usr/local/bin")
    } else {
        emptyList()
    }

/**
 * Returns the entries of [requires] that cannot be found as executables, looked
 * up the way the plugin process will see them: on the inherited PATH (plus
 * PATHEXT on Windows, and optional [extraDirs] such as Homebrew on macOS), or
 * directly when an entry is itself a path.
 */
fun findMissingExecutables(
    requires: List<String>,
    env: Map<String, String> = System.getenv(),
    isWindows: Boolean = File.separatorChar == '\\',
    extraDirs: List<String> = emptyList(),
): List<String> {
    val rawPath = env.entries.firstOrNull { it.key.equals("PATH", ignoreCase = true) }?.value
    val pathSeparator = if (isWindows) ';' else File.pathSeparatorChar
    val pathDirs =
        (
            rawPath
                ?.split(pathSeparator)
                ?.map { it.trim().removeSurrounding("\"") }
                ?.filter { it.isNotBlank() }
                .orEmpty() + extraDirs
        ).distinct()

    val rawPathext =
        env.entries.firstOrNull { it.key.equals("PATHEXT", ignoreCase = true) }?.value ?: ".COM;.EXE;.BAT;.CMD"
    val windowsExtensions =
        rawPathext
            .split(';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { if (it.startsWith('.')) it else ".$it" }

    return requires.filterNot { name ->
        val candidates =
            if (name.contains('/') || name.contains('\\')) {
                listOf(File(name))
            } else {
                pathDirs.map { File(it, name) }
            }

        val extensions =
            if (isWindows) {
                val hasExecutableExt = windowsExtensions.any { ext -> name.endsWith(ext, ignoreCase = true) }
                if (hasExecutableExt) {
                    listOf("")
                } else {
                    windowsExtensions
                }
            } else {
                listOf("")
            }

        candidates.any { base ->
            extensions.any { ext -> isExecutableFile(File(base.path + ext), isWindows) }
        }
    }
}

private fun isExecutableFile(
    file: File,
    isWindows: Boolean,
): Boolean = file.isFile && (isWindows || file.canExecute())
