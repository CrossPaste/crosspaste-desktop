package com.crosspaste.app

import com.crosspaste.config.ConfigManager
import com.crosspaste.path.DesktopAppPathProvider
import com.crosspaste.platform.getPlatform
import com.crosspaste.presist.FilePersist
import com.crosspaste.utils.getAppEnvUtils
import com.crosspaste.utils.getSystemProperty
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.BufferedReader
import java.io.InputStreamReader

class DesktopAppStartUpService(
    appLaunchState: DesktopAppLaunchState,
    configManager: ConfigManager,
) : AppStartUpService {

    private val appEnvUtils = getAppEnvUtils()

    private val currentPlatform = getPlatform()

    private val isProduction = appEnvUtils.isProduction()

    private val appStartUpService: AppStartUpService =
        if (currentPlatform.isMacos()) {
            MacAppStartUpService(configManager)
        } else if (currentPlatform.isWindows()) {
            WindowsAppStartUpService(appLaunchState, configManager)
        } else if (currentPlatform.isLinux()) {
            LinuxAppStartUpService(configManager)
        } else {
            throw IllegalStateException("Unsupported platform: $currentPlatform")
        }

    override fun followConfig() {
        if (isProduction) {
            appStartUpService.followConfig()
        }
    }

    override fun isAutoStartUp(): Boolean {
        return if (isProduction) {
            appStartUpService.isAutoStartUp()
        } else {
            false
        }
    }

    override fun makeAutoStartUp() {
        if (isProduction) {
            appStartUpService.makeAutoStartUp()
        }
    }

    override fun removeAutoStartUp() {
        if (isProduction) {
            appStartUpService.removeAutoStartUp()
        }
    }
}

class MacAppStartUpService(private val configManager: ConfigManager) : AppStartUpService {

    private val logger: KLogger = KotlinLogging.logger {}

    private val crosspasteBundleID = getSystemProperty().get("mac.bundleID")

    private val plist = "$crosspasteBundleID.plist"

    private val pathProvider = DesktopAppPathProvider

    private val filePersist = FilePersist

    override fun followConfig() {
        if (configManager.config.enableAutoStartUp) {
            makeAutoStartUp()
        } else {
            removeAutoStartUp()
        }
    }

    override fun isAutoStartUp(): Boolean {
        return pathProvider.userHome.resolve("Library/LaunchAgents/$plist").toFile().exists()
    }

    override fun makeAutoStartUp() {
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
                            pathProvider.pasteAppPath.resolve("Contents/MacOS/CrossPaste")
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

class WindowsAppStartUpService(
    appLaunchState: DesktopAppLaunchState,
    private val configManager: ConfigManager,
) : AppStartUpService {

    companion object {
        const val PFN = "ShenzhenCompileFutureTech.CrossPaste_gphsk9mrjnczc"
    }

    private val logger: KLogger = KotlinLogging.logger {}

    private val isMicrosoftStore = appLaunchState.installFrom == MICROSOFT_STORE

    private val appExePath =
        DesktopAppPathProvider.pasteAppPath
            .resolve("bin")
            .resolve("CrossPaste.exe")

    private val microsoftStartup = "explorer.exe shell:appsFolder\\$PFN!$AppName"

    private fun getRegValue(): String {
        return if (isMicrosoftStore) {
            microsoftStartup
        } else {
            appExePath.toString()
        }
    }

    override fun followConfig() {
        if (configManager.config.enableAutoStartUp) {
            makeAutoStartUp()
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
                    val registryValue = line.substringAfter("REG_SZ").trim()
                    if (registryValue.equals(getRegValue(), ignoreCase = true)) {
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

    override fun makeAutoStartUp() {
        try {
            if (!isAutoStartUp()) {
                val command = (
                    "reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run\" /v " +
                        "\"$AppName\" /d \"${getRegValue()}\" /f"
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

    private val pathProvider = DesktopAppPathProvider

    private val filePersist = FilePersist

    private val appExePath =
        pathProvider.pasteAppPath
            .resolve("bin")
            .resolve("crosspaste")

    override fun followConfig() {
        if (configManager.config.enableAutoStartUp) {
            makeAutoStartUp()
        } else {
            removeAutoStartUp()
        }
    }

    override fun isAutoStartUp(): Boolean {
        return pathProvider.userHome.resolve(".config/autostart/$desktopFile").toFile().exists()
    }

    override fun makeAutoStartUp() {
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
                        Exec=$appExePath --minimize
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
