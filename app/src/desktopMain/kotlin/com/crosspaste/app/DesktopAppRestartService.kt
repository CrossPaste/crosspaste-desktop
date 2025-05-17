package com.crosspaste.app

import com.crosspaste.path.AppPathProvider
import com.crosspaste.platform.Platform
import com.crosspaste.utils.getAppEnvUtils
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

class DesktopAppRestartService(
    platform: Platform,
    appPathProvider: AppPathProvider,
) : AppRestartService {

    private val appEnvUtils = getAppEnvUtils()

    private val appRestartService: AppRestartService =
        if (platform.isMacos()) {
            MacAppRestartService(appPathProvider)
        } else if (platform.isWindows()) {
            WindowsAppRestartService(appPathProvider)
        } else if (platform.isLinux()) {
            LinuxAppRestartService(appPathProvider)
        } else {
            throw IllegalStateException("Unsupported platform: $platform")
        }

    override fun restart(exitApplication: () -> Unit) {
        if (appEnvUtils.isProduction()) {
            appRestartService.restart(exitApplication)
        } else {
            exitApplication()
        }
    }
}

class MacAppRestartService(
    private val appPathProvider: AppPathProvider,
) : AppRestartService {

    companion object {
        private const val SCRIPT = "start.sh"
    }

    private val logger: KLogger = KotlinLogging.logger {}

    override fun restart(exitApplication: () -> Unit) {
        val pid = ProcessHandle.current().pid()
        val appPath = appPathProvider.pasteAppPath
        val restartLogPath = appPathProvider.resolve("restart.log", AppFileType.LOG)
        val scriptPath = appPath.resolve("Contents").resolve("bin").resolve(SCRIPT)
        logger.info { "Restarting app script: $scriptPath\nwith args: $pid" }
        val command =
            listOf(
                "bash",
                scriptPath.toString(),
                pid.toString(),
            )
        runCatching {
            val process =
                ProcessBuilder(command)
                    .redirectOutput(restartLogPath.toFile())
                    .redirectErrorStream(true)
                    .start()
            logger.info { "restart process pid: ${process.pid()} active: ${process.isAlive}" }
            exitApplication()
        }.onFailure { e ->
            logger.error(e) { "Failed to restart app" }
        }
    }
}

class WindowsAppRestartService(
    private val appPathProvider: AppPathProvider,
) : AppRestartService {

    companion object {
        private const val SCRIPT = "start.bat"
    }

    private val logger: KLogger = KotlinLogging.logger {}

    override fun restart(exitApplication: () -> Unit) {
        val pid = ProcessHandle.current().pid()
        val appPath = appPathProvider.pasteAppJarPath
        val restartLogPath = appPathProvider.resolve("restart.log", AppFileType.LOG)
        val scriptPath = appPath.resolve("bin").resolve(SCRIPT)
        // Path to the application's executable file, passed to the restart script
        val exeFilePath =
            appPathProvider.pasteAppExePath
                .resolve("CrossPaste.exe")

        logger.info { "Restarting app script: $scriptPath\n$exeFilePath\nwith args: $pid" }
        val command =
            listOf(
                "cmd",
                "/c",
                scriptPath.toString(),
                exeFilePath.toString(),
                pid.toString(),
            )
        runCatching {
            val process =
                ProcessBuilder(command)
                    .redirectOutput(restartLogPath.toFile())
                    .redirectErrorStream(true)
                    .start()
            logger.info { "restart process pid: ${process.pid()} active: ${process.isAlive}" }
            exitApplication()
        }.onFailure { e ->
            logger.error(e) { "Failed to restart app" }
        }
    }
}

class LinuxAppRestartService(
    private val appPathProvider: AppPathProvider,
) : AppRestartService {

    companion object {
        private const val SCRIPT = "start.sh"
    }

    private val logger: KLogger = KotlinLogging.logger {}

    override fun restart(exitApplication: () -> Unit) {
        val pid = ProcessHandle.current().pid()
        val appPath = appPathProvider.pasteAppPath
        val restartLogPath = appPathProvider.resolve("restart.log", AppFileType.LOG)
        val scriptPath = appPath.resolve("bin").resolve(SCRIPT)
        logger.info { "Restarting app script: $scriptPath\nwith args: $pid" }
        val command =
            listOf(
                "bash",
                scriptPath.toString(),
                pid.toString(),
            )
        runCatching {
            val process =
                ProcessBuilder(command)
                    .redirectOutput(restartLogPath.toFile())
                    .redirectErrorStream(true)
                    .start()
            logger.info { "restart process pid: ${process.pid()} active: ${process.isAlive}" }
            exitApplication()
        }.onFailure { e ->
            logger.error(e) { "Failed to restart app" }
        }
    }
}
