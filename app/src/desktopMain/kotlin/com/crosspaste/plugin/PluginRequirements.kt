package com.crosspaste.plugin

import java.io.File

/**
 * Returns the entries of [requires] that cannot be found as executables, looked
 * up the way the plugin process will see them: on the inherited PATH (plus
 * PATHEXT on Windows), or directly when an entry is itself a path.
 */
fun findMissingExecutables(
    requires: List<String>,
    env: Map<String, String> = System.getenv(),
    isWindows: Boolean = File.separatorChar == '\\',
): List<String> {
    val pathDirs =
        (env["PATH"] ?: env["Path"])
            ?.split(File.pathSeparatorChar)
            ?.filter { it.isNotBlank() }
            .orEmpty()
    val extensions =
        if (isWindows) {
            listOf("") + (env["PATHEXT"] ?: ".COM;.EXE;.BAT;.CMD").split(';').filter { it.isNotBlank() }
        } else {
            listOf("")
        }
    return requires.filterNot { name ->
        val candidates =
            if (name.contains('/') || name.contains('\\')) {
                listOf(File(name))
            } else {
                pathDirs.map { File(it, name) }
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
