package com.crosspaste.mouse

import com.crosspaste.utils.getFileUtils
import okio.Path
import okio.Path.Companion.toPath

/**
 * Resolves the crosspaste-mouse daemon executable. Resolution order:
 * 1. [devBinaryPath] — `mouseBinaryPath` in the developer-local development.properties,
 *    pointing at a locally built daemon binary
 * 2. [bundledDir] — binary bundled in the packaged app resources directory
 *
 * Returns null when no candidate exists on disk; the mouse feature is then
 * unavailable and the rest of the app is unaffected.
 */
class MouseDaemonBinaryLocator(
    private val devBinaryPath: String?,
    private val bundledDir: Path?,
    isWindows: Boolean,
) {

    private val fileUtils = getFileUtils()

    private val binaryName =
        if (isWindows) {
            "$BINARY_BASE_NAME.exe"
        } else {
            BINARY_BASE_NAME
        }

    fun locate(): Path? = candidates().firstOrNull { fileUtils.existFile(it) }

    private fun candidates(): List<Path> =
        listOfNotNull(
            devBinaryPath?.toPath(normalize = true),
            bundledDir?.resolve(binaryName),
        )

    companion object {
        const val BINARY_BASE_NAME = "crosspaste-mouse"
    }
}
