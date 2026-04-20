package com.crosspaste.mouse

import java.io.File

object MouseDaemonBinary {
    private const val SYSTEM_PROPERTY = "crosspaste.mouse.binary"
    private const val ENV_VAR = "CROSSPASTE_MOUSE_BIN"

    /**
     * Resolution order:
     *   1. -Dcrosspaste.mouse.binary=<path>
     *   2. $CROSSPASTE_MOUSE_BIN
     *   3. Any path in `candidatePaths` (e.g. bundled in resources/bin/...)
     * Returns null if nothing points at an existing regular file.
     */
    fun resolve(
        envLookup: (String) -> String? = System::getenv,
        candidatePaths: List<String> = defaultCandidatePaths(),
    ): File? {
        System.getProperty(SYSTEM_PROPERTY)?.let { File(it).takeIf { f -> f.isFile } }?.let { return it }
        envLookup(ENV_VAR)?.let { File(it).takeIf { f -> f.isFile } }?.let { return it }
        return candidatePaths
            .asSequence()
            .map(::File)
            .firstOrNull { it.isFile }
    }

    private fun defaultCandidatePaths(): List<String> {
        // Production bundling is a follow-up plan; for now the only
        // candidate is a dev-mode symlink next to the app jar.
        val home = System.getProperty("user.home") ?: return emptyList()
        return listOf("$home/crosspaste-mouse/target/release/crosspaste-mouse")
    }
}
