package com.crosspaste.cli.platform

import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.Path
import platform.posix.system
import kotlin.experimental.ExperimentalNativeApi

interface AppLauncher {

    fun launch(): Boolean
}

/**
 * Launches the desktop app directly (decision D6): `open` on the .app bundle
 * on macOS, the Conveyor JVM launcher executable elsewhere. The start.sh /
 * start.bat scripts are NOT used here — their job is the app's own
 * wait-for-old-pid update restart (DesktopAppRestartService), and unlike the
 * launcher they don't exist in every channel (the Linux tarball has no
 * root-inputs, so no start.sh).
 *
 * The shell commands below background or detach the launch, so a missing
 * target would still "succeed" and leave the caller waiting out the full
 * readiness timeout — check it up front instead.
 */
private fun launchTargetExists(target: Path): Boolean = FileSystem.SYSTEM.exists(target)

@OptIn(ExperimentalNativeApi::class)
fun createAppLauncher(
    appPathProvider: CliAppPathProvider,
    headless: Boolean = false,
): AppLauncher {
    val os = Platform.osFamily
    return when (os) {
        OsFamily.MACOSX -> MacosAppLauncher(appPathProvider)
        OsFamily.LINUX -> LinuxAppLauncher(appPathProvider, headless)
        OsFamily.WINDOWS -> WindowsAppLauncher(appPathProvider)
        else -> throw IllegalStateException("Unsupported platform: $os")
    }
}

@OptIn(ExperimentalForeignApi::class)
class MacosAppLauncher(
    private val appPathProvider: CliAppPathProvider,
) : AppLauncher {

    override fun launch(): Boolean {
        val bundle = appPathProvider.resolveGuiLaunchTarget()
        if (!launchTargetExists(bundle)) return false
        val exitCode = system("open \"$bundle\"")
        return exitCode == 0
    }
}

@OptIn(ExperimentalForeignApi::class)
class LinuxAppLauncher(
    private val appPathProvider: CliAppPathProvider,
    private val headless: Boolean = false,
) : AppLauncher {

    override fun launch(): Boolean {
        val launcher = appPathProvider.resolveGuiLaunchTarget()
        if (!launchTargetExists(launcher)) return false
        val exitCode = system(buildLinuxLaunchCommand(launcher, headless))
        return exitCode == 0
    }
}

/**
 * The app would auto-detect a missing DISPLAY anyway, but passing --headless
 * makes the daemon intent explicit rather than relying on AWT detection.
 */
internal fun buildLinuxLaunchCommand(
    launcher: Path,
    headless: Boolean,
): String {
    val headlessArg = if (headless) " --headless" else ""
    return "nohup \"$launcher\"$headlessArg > /dev/null 2>&1 &"
}

@OptIn(ExperimentalForeignApi::class)
class WindowsAppLauncher(
    private val appPathProvider: CliAppPathProvider,
) : AppLauncher {

    override fun launch(): Boolean {
        val exe = appPathProvider.resolveGuiLaunchTarget()
        if (!launchTargetExists(exe)) return false
        // system() runs via cmd.exe; `start ""` detaches so the CLI does not
        // block on the app process (the empty "" is the window title slot).
        val exitCode = system("start \"\" \"$exe\"")
        return exitCode == 0
    }
}
