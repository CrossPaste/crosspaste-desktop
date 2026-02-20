package com.crosspaste.cli.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.system
import kotlin.experimental.ExperimentalNativeApi

interface AppLauncher {

    fun launch(): Boolean
}

@OptIn(ExperimentalNativeApi::class)
fun createAppLauncher(appPathProvider: CliAppPathProvider): AppLauncher {
    val os = Platform.osFamily
    return when (os) {
        OsFamily.MACOSX -> MacosAppLauncher(appPathProvider)
        OsFamily.LINUX -> LinuxAppLauncher(appPathProvider)
        OsFamily.WINDOWS -> WindowsAppLauncher(appPathProvider)
        else -> throw IllegalStateException("Unsupported platform: $os")
    }
}

@OptIn(ExperimentalForeignApi::class)
class MacosAppLauncher(
    private val appPathProvider: CliAppPathProvider,
) : AppLauncher {

    override fun launch(): Boolean {
        val script = appPathProvider.startScriptPath
        val exitCode = system("nohup bash \"$script\" > /dev/null 2>&1 &")
        return exitCode == 0
    }
}

@OptIn(ExperimentalForeignApi::class)
class LinuxAppLauncher(
    private val appPathProvider: CliAppPathProvider,
) : AppLauncher {

    override fun launch(): Boolean {
        val script = appPathProvider.startScriptPath
        val exitCode = system("nohup bash \"$script\" > /dev/null 2>&1 &")
        return exitCode == 0
    }
}

@OptIn(ExperimentalForeignApi::class)
class WindowsAppLauncher(
    private val appPathProvider: CliAppPathProvider,
) : AppLauncher {

    override fun launch(): Boolean {
        val script = appPathProvider.startScriptPath
        val exePath = appPathProvider.resolveAppExePath()
        val exitCode = system("\"$script\" \"$exePath\"")
        return exitCode == 0
    }
}
