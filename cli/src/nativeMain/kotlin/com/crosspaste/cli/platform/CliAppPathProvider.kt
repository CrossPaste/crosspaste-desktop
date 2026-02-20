package com.crosspaste.cli.platform

import execpath.get_executable_path
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import okio.Path
import okio.Path.Companion.toPath
import kotlin.experimental.ExperimentalNativeApi

/**
 * Provides application paths for the CLI binary, derived from the
 * executable's own location. The CLI binary is bundled inside the
 * app package with a fixed directory structure relative to start scripts.
 *
 * macOS (.app bundle):
 *   CrossPaste.app/                     ← appPath
 *     Contents/
 *       bin/
 *         crosspaste                    ← CLI binary (binDir)
 *         start.sh                      ← start script
 *       MacOS/CrossPaste                ← JVM launcher
 *
 * Linux:
 *   /usr/lib/crosspaste/               ← appPath
 *     bin/
 *       crosspaste-cli                  ← CLI binary (binDir)
 *       start.sh                        ← start script
 *       crosspaste                      ← JVM launcher
 *
 * Windows:
 *   CrossPaste/                         ← appPath
 *     bin/
 *       crosspaste.exe                  ← CLI binary (binDir)
 *       CrossPaste.exe                  ← JVM launcher
 *       start.bat                       ← start script
 */
class CliAppPathProvider {

    val binDir: Path

    val appPath: Path

    val startScriptPath: Path

    init {
        val execDir = resolveExecutableDir()
        binDir = execDir
        val os = getPlatformOsFamily()
        appPath = resolveAppPath(execDir, os)
        startScriptPath = resolveStartScriptPath(execDir, os)
    }

    private fun resolveAppPath(
        binDir: Path,
        os: OsFamilyCompat,
    ): Path {
        val parent = binDir.parent ?: binDir
        return when (os) {
            // bin/ → Contents/ → CrossPaste.app/
            OsFamilyCompat.MACOSX -> parent.parent ?: parent
            // bin/ → /usr/lib/crosspaste/
            OsFamilyCompat.LINUX -> parent
            // bin/ → CrossPaste/
            OsFamilyCompat.WINDOWS -> parent
        }
    }

    private fun resolveStartScriptPath(
        binDir: Path,
        os: OsFamilyCompat,
    ): Path =
        when (os) {
            OsFamilyCompat.MACOSX -> binDir.resolve("start.sh")
            OsFamilyCompat.LINUX -> binDir.resolve("start.sh")
            OsFamilyCompat.WINDOWS -> binDir.resolve("start.bat")
        }

    /**
     * On Windows, start.bat requires the exe path as its first argument.
     */
    fun resolveAppExePath(): Path = binDir.resolve("CrossPaste.exe")
}

@OptIn(ExperimentalForeignApi::class)
private fun resolveExecutableDir(): Path {
    val exePath =
        get_executable_path()?.toKString()
            ?: throw IllegalStateException("Cannot determine CLI executable path")
    val path = exePath.toPath(normalize = true)
    return path.parent ?: path
}

/**
 * Simplified OS family enum usable without @ExperimentalNativeApi on each call site.
 */
private enum class OsFamilyCompat { MACOSX, LINUX, WINDOWS }

@OptIn(ExperimentalNativeApi::class)
private fun getPlatformOsFamily(): OsFamilyCompat =
    when (Platform.osFamily) {
        OsFamily.MACOSX -> OsFamilyCompat.MACOSX
        OsFamily.LINUX -> OsFamilyCompat.LINUX
        OsFamily.WINDOWS -> OsFamilyCompat.WINDOWS
        else -> throw IllegalStateException("Unsupported platform: ${Platform.osFamily}")
    }
