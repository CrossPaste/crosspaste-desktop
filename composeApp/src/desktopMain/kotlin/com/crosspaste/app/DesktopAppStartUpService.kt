package com.crosspaste.app

import com.crosspaste.config.ConfigManager
import com.crosspaste.path.DesktopPathProvider
import com.crosspaste.platform.currentPlatform
import com.crosspaste.presist.DesktopFilePersist
import com.crosspaste.utils.getSystemProperty
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.io.path.absolutePathString

class DesktopAppStartUpService(configManager: ConfigManager) : AppStartUpService {

    private val currentPlatform = currentPlatform()

    private val appStartUpService: AppStartUpService =
        if (currentPlatform.isMacos()) {
            MacAppStartUpService(configManager)
        } else if (currentPlatform.isWindows()) {
            WindowsAppStartUpService(configManager)
        } else if (currentPlatform.isLinux()) {
            LinuxAppStartUpService(configManager)
        } else {
            throw IllegalStateException("Unsupported platform: $currentPlatform")
        }

    override fun followConfig() {
        appStartUpService.followConfig()
    }

    override fun isAutoStartUp(): Boolean {
        return appStartUpService.isAutoStartUp()
    }

    override fun makeAutoStatUp() {
        appStartUpService.makeAutoStatUp()
    }

    override fun removeAutoStartUp() {
        appStartUpService.removeAutoStartUp()
    }
}

class MacAppStartUpService(private val configManager: ConfigManager) : AppStartUpService {

    private val logger: KLogger = KotlinLogging.logger {}

    private val crosspasteBundleID = getSystemProperty().get("mac.bundleID")

    private val plist = "$crosspasteBundleID.plist"

    private val pathProvider = DesktopPathProvider

    private val filePersist = DesktopFilePersist

    override fun followConfig() {
        if (configManager.config.enableAutoStartUp) {
            makeAutoStatUp()
        } else {
            removeAutoStartUp()
        }
    }

    override fun isAutoStartUp(): Boolean {
        return pathProvider.userHome.resolve("Library/LaunchAgents/$plist").toFile().exists()
    }

    override fun makeAutoStatUp() {
        try {
            if (!isAutoStartUp()) {
                logger.info { "Make auto startup" }
                val plistPath = pathProvider.userHome.resolve("Library/LaunchAgents/$plist")
                filePersist.createOneFilePersist(plistPath)
                    .saveBytes(
                        """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                        <plist version="1.0">
                        <dict>
                            <key>Label</key>
                            <string>$crosspasteBundleID</string>
                            <key>ProgramArguments</key>
                            <array>
                                <string>${
                            pathProvider.clipAppPath.resolve("Contents/MacOS/CrossPaste").absolutePathString()
                        }</string>
                                <string>--minimize</string>
                            </array>
                            <key>RunAtLoad</key>
                            <true/>
                        </dict>
                        </plist>
                        """.trimIndent().toByteArray(),
                    )
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to make auto startup" }
        }
    }

    override fun removeAutoStartUp() {
        try {
            if (isAutoStartUp()) {
                logger.info { "Remove auto startup" }
                pathProvider.userHome.resolve("Library/LaunchAgents/$plist").toFile().delete()
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to remove auto startup" }
        }
    }
}

class WindowsAppStartUpService(private val configManager: ConfigManager) : AppStartUpService {

    private val logger: KLogger = KotlinLogging.logger {}

    private val appExePath =
        DesktopPathProvider.clipAppPath
            .resolve("bin")
            .resolve("crosspaste.exe")

    override fun followConfig() {
        if (configManager.config.enableAutoStartUp) {
            makeAutoStatUp()
        } else {
            removeAutoStartUp()
        }
    }

    override fun isAutoStartUp(): Boolean {
        val command = "reg query \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run\" /v \"$AppName\""
        try {
            val process = Runtime.getRuntime().exec(command)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line!!.contains("REG_SZ")) {
                    val registryPath = line!!.substringAfter("REG_SZ").trim()
                    if (registryPath.equals(appExePath.absolutePathString(), ignoreCase = true)) {
                        logger.info { "$AppName is set to start on boot with the correct path." }
                        return true
                    } else {
                        logger.info { "$AppName is set to start on boot with the path is not current path." }
                        return false
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to check if $AppName is set to start on boot." }
        }
        logger.info { "$AppName is not set to start on boot." }
        return false
    }

    override fun makeAutoStatUp() {
        try {
            if (!isAutoStartUp()) {
                val command = (
                    "reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run\" /v " +
                        "\"$AppName\" /d \"${appExePath.absolutePathString()}\" /f"
                )
                val process = Runtime.getRuntime().exec(command)
                process.waitFor()
                logger.info { "Command executed successfully: $command" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to make auto startup" }
        }
    }

    override fun removeAutoStartUp() {
        try {
            if (isAutoStartUp()) {
                val command = "reg delete \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run\" /v \"$AppName\" /f"
                val process = Runtime.getRuntime().exec(command)
                process.waitFor()
                logger.info { "Command executed successfully: $command" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to remove auto startup" }
        }
    }
}

class LinuxAppStartUpService(private val configManager: ConfigManager) : AppStartUpService {

    private val logger: KLogger = KotlinLogging.logger {}

    private val desktopFile = "crosspaste.desktop"

    private val pathProvider = DesktopPathProvider

    private val filePersist = DesktopFilePersist

    private val appExePath =
        DesktopPathProvider.clipAppPath
            .resolve("bin")
            .resolve("crosspaste")

    override fun followConfig() {
        if (configManager.config.enableAutoStartUp) {
            makeAutoStatUp()
        } else {
            removeAutoStartUp()
        }
    }

    override fun isAutoStartUp(): Boolean {
        return pathProvider.userHome.resolve(".config/autostart/$desktopFile").toFile().exists()
    }

    override fun makeAutoStatUp() {
        try {
            if (!isAutoStartUp()) {
                logger.info { "Make auto startup" }
                val desktopFilePath = pathProvider.userHome.resolve(".config/autostart/$desktopFile")
                filePersist.createOneFilePersist(desktopFilePath)
                    .saveBytes(
                        """
                        [Desktop Entry]
                        Type=Application
                        Name=CrossPaste
                        Exec=${appExePath.absolutePathString()} --minimize
                        Categories=Utility
                        Terminal=false
                        X-GNOME-Autostart-enabled=true
                        X-GNOME-Autostart-Delay=10
                        X-MATE-Autostart-Delay=10
                        X-KDE-autostart-after=panel
                        """.trimIndent().toByteArray(),
                    )
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to make auto startup" }
        }
    }

    override fun removeAutoStartUp() {
        try {
            if (isAutoStartUp()) {
                logger.info { "Remove auto startup" }
                pathProvider.userHome.resolve(".config/autostart/$desktopFile").toFile().delete()
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to remove auto startup" }
        }
    }
}