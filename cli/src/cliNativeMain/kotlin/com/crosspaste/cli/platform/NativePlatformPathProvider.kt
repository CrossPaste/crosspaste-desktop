package com.crosspaste.cli.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import okio.Path
import okio.Path.Companion.toPath
import kotlin.experimental.ExperimentalNativeApi

/**
 * Platform-specific path provider for the CLI native binary.
 * Mirrors the exact path logic from the desktop app:
 * - macOS: ~/Library/Application Support/CrossPaste/
 * - Linux: ~/.local/share/.crosspaste/
 * - Windows: ~/.crosspaste/
 */
interface NativePlatformPathProvider {

    companion object {
        const val CROSSPASTE_DIR_NAME = ".crosspaste"

        /**
         * Overrides the app user-data directory the CLI targets. Meant for
         * development: point it at a dev instance's storage (e.g. `app/.user`
         * for `./gradlew app:run`) so the CLI finds that instance's
         * cli-endpoint.json instead of the installed app's.
         */
        const val USER_DATA_DIR_ENV = "CROSSPASTE_USER_DATA_DIR"
    }

    fun getDefaultUserDataPath(): Path
}

fun userDataDirOverride(): String? = getEnv(NativePlatformPathProvider.USER_DATA_DIR_ENV)?.takeIf { it.isNotBlank() }

@OptIn(ExperimentalNativeApi::class)
fun createNativePlatformPathProvider(): NativePlatformPathProvider {
    userDataDirOverride()?.let { override ->
        val path = override.toPath(normalize = true)
        return object : NativePlatformPathProvider {
            override fun getDefaultUserDataPath(): Path = path
        }
    }
    return when (val os = Platform.osFamily) {
        OsFamily.MACOSX -> MacosNativePlatformPathProvider()
        OsFamily.LINUX -> LinuxNativePlatformPathProvider()
        OsFamily.WINDOWS -> WindowsNativePlatformPathProvider()
        else -> throw IllegalStateException("Unsupported platform: $os")
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun getEnv(name: String): String? = platform.posix.getenv(name)?.toKString()

private fun getHomeDir(): String = getEnv("HOME") ?: getEnv("USERPROFILE") ?: "/tmp"

class MacosNativePlatformPathProvider : NativePlatformPathProvider {

    private val userHome = getHomeDir()

    override fun getDefaultUserDataPath(): Path = "$userHome/Library/Application Support/CrossPaste".toPath()
}

class LinuxNativePlatformPathProvider : NativePlatformPathProvider {

    private val userHome = getHomeDir()

    override fun getDefaultUserDataPath(): Path =
        "$userHome/.local/share/${NativePlatformPathProvider.CROSSPASTE_DIR_NAME}".toPath()
}

class WindowsNativePlatformPathProvider : NativePlatformPathProvider {

    private val userHome = getEnv("USERPROFILE") ?: getHomeDir()

    override fun getDefaultUserDataPath(): Path = "$userHome/${NativePlatformPathProvider.CROSSPASTE_DIR_NAME}".toPath()
}
