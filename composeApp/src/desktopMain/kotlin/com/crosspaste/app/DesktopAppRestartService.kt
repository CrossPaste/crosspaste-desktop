package com.crosspaste.app

import com.crosspaste.path.DesktopAppPathProvider
import com.crosspaste.platform.getPlatform
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

object DesktopAppRestartService : AppRestartService {

    private val currentPlatform = getPlatform()

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
        if (AppEnv.CURRENT.isProduction()) {
            appRestartService.restart(exitApplication)
        } else {
            exitApplication()
        }
    }
}

class MacAppRestartService : AppRestartService {

    companion object {
        private const val SCRIPT = "start.sh"
    }

    private val logger: KLogger = KotlinLogging.logger {}

    override fun restart(exitApplication: () -> Unit) {
        val pid = ProcessHandle.current().pid()
        val appPath = DesktopAppPathProvider.pasteAppPath
        val restartLogPath = DesktopAppPathProvider.resolve("restart.log", AppFileType.LOG)
        val scriptPath = appPath.resolve("Contents").resolve("bin").resolve(SCRIPT)
        logger.info { "Restarting app script: $scriptPath\nwith args: $pid" }
        val command =
            listOf(
                "bash",
                scriptPath.toString(),
                pid.toString(),
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
        private const val SCRIPT = "start.bat"
    }

    private val logger: KLogger = KotlinLogging.logger {}

    override fun restart(exitApplication: () -> Unit) {
        val pid = ProcessHandle.current().pid()
        val appPath = DesktopAppPathProvider.pasteAppJarPath
        val restartLogPath = DesktopAppPathProvider.resolve("restart.log", AppFileType.LOG)
        val scriptPath = appPath.resolve("bin").resolve(SCRIPT)

        logger.info { "Restarting app script: $scriptPath\nwith args: $pid" }
        val command =
            listOf(
                "cmd",
                "/c",
                scriptPath.toString(),
                pid.toString(),
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

    companion object {
        private const val SCRIPT = "start.sh"
    }

    private val logger: KLogger = KotlinLogging.logger {}

    override fun restart(exitApplication: () -> Unit) {
        val pid = ProcessHandle.current().pid()
        val appPath = DesktopAppPathProvider.pasteAppPath
        val restartLogPath = DesktopAppPathProvider.resolve("restart.log", AppFileType.LOG)
        val scriptPath = appPath.resolve("bin").resolve(SCRIPT)
        logger.info { "Restarting app script: $scriptPath\nwith args: $pid" }
        val command =
            listOf(
                "bash",
                scriptPath.toString(),
                pid.toString(),
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
