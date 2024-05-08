package com.clipevery.app

import com.clipevery.path.DesktopPathProvider
import com.clipevery.platform.currentPlatform
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.io.path.absolutePathString

object DesktopAppRestartService : AppRestartService {

    private val currentPlatform = currentPlatform()

    private val appRestartService: AppRestartService =
        if (currentPlatform.isMacos()) {
            MacAppRestartService()
        } else if (currentPlatform.isWindows()) {
            WindowsAppRestartService()
        } else if (currentPlatform.isLinux()) {
            LinuxAppRestartService()
        } else {
            throw IllegalStateException("Unsupported platform: $currentPlatform")
        }

    override fun restart(exitApplication: () -> Unit) {
        if (AppEnv.isProduction()) {
            appRestartService.restart(exitApplication)
        } else {
            exitApplication()
        }
    }
}

class MacAppRestartService : AppRestartService {

    companion object {
        private const val SCRIPT = "restart.sh"
    }

    private val logger: KLogger = KotlinLogging.logger {}

    override fun restart(exitApplication: () -> Unit) {
        val pid = ProcessHandle.current().pid()
        val appPath = DesktopPathProvider.clipAppPath
        val restartLogPath = DesktopPathProvider.resolve("restart.log", AppFileType.LOG)
        val scriptPath = appPath.resolve("Resources").resolve("bin").resolve(SCRIPT)
        val openAppPath = appPath.parent
        logger.info { "Restarting app script: $scriptPath\nwith args: $pid $openAppPath" }
        val command =
            listOf(
                "bash",
                scriptPath.absolutePathString(),
                pid.toString(),
                openAppPath.absolutePathString(),
            )
        try {
            val process =
                ProcessBuilder(command)
                    .redirectOutput(restartLogPath.toFile())
                    .redirectErrorStream(true)
                    .start()
            logger.info { "restart process pid: ${process.pid()} active: ${process.isAlive}" }
            exitApplication()
        } catch (e: Exception) {
            logger.error(e) { "Failed to restart app" }
        }
    }
}

class WindowsAppRestartService : AppRestartService {

    companion object {
        private const val SCRIPT = "restart.bat"
    }

    private val logger: KLogger = KotlinLogging.logger {}

    override fun restart(exitApplication: () -> Unit) {
        val pid = ProcessHandle.current().pid()
        val appPath = DesktopPathProvider.clipAppPath
        val restartLogPath = DesktopPathProvider.resolve("restart.log", AppFileType.LOG)
        val scriptPath = appPath.resolve("app").resolve("bin").resolve(SCRIPT)
        val appExePath = appPath.resolve("bin").resolve("clipevery.exe")

        logger.info { "Restarting app script: $scriptPath\nwith args: $pid $appExePath" }
        val command =
            listOf(
                "cmd",
                "/c",
                "\"${scriptPath.absolutePathString()}\" $pid \"${appExePath.absolutePathString()}\"",
            )
        try {
            val process =
                ProcessBuilder(command)
                    .redirectOutput(restartLogPath.toFile())
                    .redirectErrorStream(true)
                    .start()
            logger.info { "restart process pid: ${process.pid()} active: ${process.isAlive}" }
            exitApplication()
        } catch (e: Exception) {
            logger.error(e) { "Failed to restart app" }
        }
    }
}

class LinuxAppRestartService : AppRestartService {
    override fun restart(exitApplication: () -> Unit) {
        TODO("Not yet implemented")
    }
}
