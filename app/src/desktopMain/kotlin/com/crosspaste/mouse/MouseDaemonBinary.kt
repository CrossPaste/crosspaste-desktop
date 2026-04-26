package com.crosspaste.mouse

import com.crosspaste.platform.Platform
import java.io.File

object MouseDaemonBinary {
    private const val SYSTEM_PROPERTY = "crosspaste.mouse.binary"
    private const val ENV_VAR = "CROSSPASTE_MOUSE_BIN"

    /**
     * Resolution order:
     *   1. -Dcrosspaste.mouse.binary=<path>     (per-launch override)
     *   2. $CROSSPASTE_MOUSE_BIN                (per-shell override)
     *   3. Any path in [candidatePaths]         (caller-supplied, env-aware)
     *
     * The caller is responsible for assembling [candidatePaths] from the
     * dev-mode `DevConfig.mouseBinaryPath` or the prod-mode
     * `pasteAppExePath / binaryName(platform)`. Returns null if nothing
     * points at an existing regular file.
     */
    fun resolve(
        candidatePaths: List<String> = emptyList(),
        envLookup: (String) -> String? = System::getenv,
    ): File? {
        System.getProperty(SYSTEM_PROPERTY)?.let { File(it).takeIf { f -> f.isFile } }?.let { return it }
        envLookup(ENV_VAR)?.let { File(it).takeIf { f -> f.isFile } }?.let { return it }
        return candidatePaths
            .asSequence()
            .filter { it.isNotBlank() }
            .map(::File)
            .firstOrNull { it.isFile }
    }

    /** crosspaste-mouse build artifact name for the given platform. */
    fun binaryName(platform: Platform): String =
        if (platform.isWindows()) "crosspaste-mouse.exe" else "crosspaste-mouse"
}
