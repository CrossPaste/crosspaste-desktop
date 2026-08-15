package com.crosspaste.cli.platform

import execpath.get_executable_path
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import okio.Path
import okio.Path.Companion.toPath
import kotlin.experimental.ExperimentalNativeApi

/**
 * Provides application paths for the CLI binary, derived from the
 * executable's own location. The CLI ships as application payload
 * (decision D6): a plain Conveyor input that lands in the JVM payload
 * area of every package and channel.
 *
 * macOS (.app bundle):
 *   CrossPaste.app/                     ← appPath (launch via `open`)
 *     Contents/
 *       Resources/
 *         bin/crosspaste-cli            ← CLI binary (binDir)
 *       MacOS/CrossPaste                ← JVM launcher
 *
 * Linux (deb: /usr/lib/crosspaste; tarball/AppImage: the extracted root):
 *   <root>/                             ← appPath
 *     bin/crosspaste                    ← JVM launcher
 *     lib/app/
 *       bin/crosspaste-cli              ← CLI binary (binDir)
 *
 * Windows (installed MSIX tree or extracted portable zip):
 *   <root>/                             ← appPath
 *     bin/CrossPaste.exe                ← JVM launcher
 *     app/
 *       bin/crosspaste-cli.exe          ← CLI binary (binDir)
 */
class CliAppPathProvider {

    val binDir: Path

    val appPath: Path

    init {
        val execDir = resolveExecutableDir()
        binDir = execDir
        appPath = resolveAppPath(execDir, getPlatformOsFamily())
    }

    private fun resolveAppPath(
        binDir: Path,
        os: OsFamilyCompat,
    ): Path {
        fun up(
            path: Path,
            levels: Int,
        ): Path {
            var current = path
            repeat(levels) { current = current.parent ?: current }
            return current
        }
        return when (os) {
            // Resources/bin/ → Resources/ → Contents/ → CrossPaste.app/
            OsFamilyCompat.MACOSX -> up(binDir, 3)
            // lib/app/bin/ → lib/app/ → lib/ → <root>/
            OsFamilyCompat.LINUX -> up(binDir, 3)
            // app\bin\ → app\ → <root>\
            OsFamilyCompat.WINDOWS -> up(binDir, 2)
        }
    }

    /**
     * The thing to launch to bring up the desktop app: the .app bundle on
     * macOS (started via `open`), the Conveyor JVM launcher elsewhere.
     */
    fun resolveGuiLaunchTarget(): Path =
        when (getPlatformOsFamily()) {
            OsFamilyCompat.MACOSX -> appPath
            OsFamilyCompat.LINUX -> appPath.resolve("bin/crosspaste")
            OsFamilyCompat.WINDOWS -> appPath.resolve("bin/CrossPaste.exe")
        }
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
