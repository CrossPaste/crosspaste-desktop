package com.crosspaste.app

import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.path.AppPathProvider
import com.crosspaste.platform.Platform
import com.crosspaste.presist.FilePersist
import com.crosspaste.utils.getAppEnvUtils
import com.crosspaste.utils.getSystemProperty
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

class DesktopAppStartUpService(
    appLaunchState: DesktopAppLaunchState,
    appPathProvider: AppPathProvider,
    configManager: DesktopConfigManager,
    platform: Platform,
) : AppStartUpService {

    private val appEnvUtils = getAppEnvUtils()

    private val isProduction = appEnvUtils.isProduction()

    private val appStartUpService: AppStartUpService =
        if (platform.isMacos()) {
            MacAppStartUpService(configManager, appPathProvider)
        } else if (platform.isWindows()) {
            WindowsAppStartUpService(appLaunchState, appPathProvider, configManager)
        } else if (platform.isLinux()) {
            LinuxAppStartUpService(appPathProvider, configManager)
        } else {
            throw IllegalStateException("Unsupported platform: $platform")
        }

    override fun followConfig() {
        if (isProduction) {
            appStartUpService.followConfig()
        }
    }

    override fun isAutoStartUp(): Boolean =
        if (isProduction) {
            appStartUpService.isAutoStartUp()
        } else {
            false
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

class MacAppStartUpService(
    private val configManager: DesktopConfigManager,
    private val appPathProvider: AppPathProvider,
) : AppStartUpService {

    private val logger: KLogger = KotlinLogging.logger {}

    private val crosspasteBundleID = getSystemProperty().get("mac.bundleID")

    private val plist = "$crosspasteBundleID.plist"

    private val filePersist = FilePersist

    override fun followConfig() {
        if (configManager.getCurrentConfig().enableAutoStartUp) {
            makeAutoStartUp()
        } else {
            removeAutoStartUp()
        }
    }

    override fun isAutoStartUp(): Boolean =
        appPathProvider.userHome
            .resolve("Library/LaunchAgents/$plist")
            .toFile()
            .exists()

    override fun makeAutoStartUp() {
        runCatching {
            if (!isAutoStartUp()) {
                logger.info { "Make auto startup" }
                val plistPath = appPathProvider.userHome.resolve("Library/LaunchAgents/$plist")
                filePersist
                    .createOneFilePersist(plistPath)
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
                            appPathProvider.pasteAppPath.resolve("Contents/MacOS/CrossPaste")
                        }</string>
                                <string>--minimize</string>
                            </array>
                            <key>RunAtLoad</key>
                            <true/>
                        </dict>
                        </plist>
                        """.trimIndent().encodeToByteArray(),
                    )
            }
        }.onFailure { e ->
            logger.error(e) { "Failed to make auto startup" }
        }
    }

    override fun removeAutoStartUp() {
        runCatching {
            if (isAutoStartUp()) {
                logger.info { "Remove auto startup" }
                appPathProvider.userHome
                    .resolve("Library/LaunchAgents/$plist")
                    .toFile()
                    .delete()
            }
        }.onFailure { e ->
            logger.error(e) { "Failed to remove auto startup" }
        }
    }
}

class WindowsAppStartUpService(
    appLaunchState: DesktopAppLaunchState,
    appPathProvider: AppPathProvider,
    private val configManager: DesktopConfigManager,
) : AppStartUpService {

    companion object {
        const val PFN = "ShenzhenCompileFutureTech.CrossPaste_gphsk9mrjnczc"
    }

    private val logger: KLogger = KotlinLogging.logger {}

    private val isMicrosoftStore = appLaunchState.installFrom == MICROSOFT_STORE

    private val appExePath =
        appPathProvider.pasteAppPath
            .resolve("bin")
            .resolve("CrossPaste.exe")

    private val microsoftStartup = "explorer.exe shell:appsFolder\\$PFN!$AppName"

    private fun getRegValue(): String =
        if (isMicrosoftStore) {
            microsoftStartup
        } else {
            appExePath.toString()
        }

    override fun followConfig() {
        if (configManager.getCurrentConfig().enableAutoStartUp) {
            makeAutoStartUp()
        } else {
            removeAutoStartUp()
        }
    }

    override fun isAutoStartUp(): Boolean {
        val command =
            listOf(
                "reg",
                "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                "/v",
                AppName,
            )

        runCatching {
            val process =
                ProcessBuilder()
                    .command(command)
                    .start()

            val result =
                process.inputStream.bufferedReader().use { reader ->
                    reader.lineSequence().firstOrNull { it.contains("REG_SZ") }
                }
            process.waitFor()

            if (result != null) {
                val registryValue = result.substringAfter("REG_SZ").trim()
                if (registryValue.equals(getRegValue(), ignoreCase = true)) {
                    logger.info { "$AppName is set to start on boot with the correct path." }
                    return true
                } else {
                    logger.info { "$AppName is set to start on boot with the path is not current path." }
                    return false
                }
            }
        }.onFailure { e ->
            logger.error(e) { "Failed to check if $AppName is set to start on boot." }
        }
        logger.info { "$AppName is not set to start on boot." }
        return false
    }

    override fun makeAutoStartUp() {
        runCatching {
            if (!isAutoStartUp()) {
                val command =
                    listOf(
                        "reg",
                        "add",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                        "/v",
                        AppName,
                        "/d",
                        getRegValue(),
                        "/f",
                    )
                val process =
                    ProcessBuilder()
                        .command(command)
                        .redirectErrorStream(true)
                        .start()

                process.inputStream.bufferedReader().use { it.readText() }
                val exitCode = process.waitFor()

                if (exitCode == 0) {
                    logger.info { "Command executed successfully: $command" }
                } else {
                    logger.warn { "Command exited with code $exitCode: ${command.joinToString(" ")}" }
                }
            }
        }.onFailure { e ->
            logger.error(e) { "Failed to make auto startup" }
        }
    }

    override fun removeAutoStartUp() {
        runCatching {
            if (isAutoStartUp()) {
                val command =
                    listOf(
                        "reg",
                        "delete",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                        "/v",
                        AppName,
                        "/f",
                    )

                val process =
                    ProcessBuilder()
                        .command(command)
                        .redirectErrorStream(true)
                        .start()

                process.inputStream.bufferedReader().use { it.readText() }
                val exitCode = process.waitFor()

                if (exitCode == 0) {
                    logger.info { "Auto startup removed successfully for $AppName" }
                } else {
                    logger.warn { "Command exited with code $exitCode: ${command.joinToString(" ")}" }
                }
            }
        }.onFailure { e ->
            logger.error(e) { "Failed to remove auto startup" }
        }
    }
}

class LinuxAppStartUpService(
    private val appPathProvider: AppPathProvider,
    private val configManager: DesktopConfigManager,
) : AppStartUpService {

    private val logger: KLogger = KotlinLogging.logger {}

    private val desktopFile = "crosspaste.desktop"

    private val filePersist = FilePersist

    private val appExePath =
        appPathProvider.pasteAppPath
            .resolve("bin")
            .resolve("crosspaste")

    override fun followConfig() {
        if (configManager.getCurrentConfig().enableAutoStartUp) {
            makeAutoStartUp()
        } else {
            removeAutoStartUp()
        }
    }

    override fun isAutoStartUp(): Boolean =
        appPathProvider.userHome
            .resolve(".config/autostart/$desktopFile")
            .toFile()
            .exists()

    override fun makeAutoStartUp() {
        runCatching {
            if (!isAutoStartUp()) {
                logger.info { "Make auto startup" }
                val desktopFilePath = appPathProvider.userHome.resolve(".config/autostart/$desktopFile")
                filePersist
                    .createOneFilePersist(desktopFilePath)
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
                        """.trimIndent().encodeToByteArray(),
                    )
            }
        }.onFailure { e ->
            logger.error(e) { "Failed to make auto startup" }
        }
    }

    override fun removeAutoStartUp() {
        runCatching {
            if (isAutoStartUp()) {
                logger.info { "Remove auto startup" }
                appPathProvider.userHome
                    .resolve(".config/autostart/$desktopFile")
                    .toFile()
                    .delete()
            }
        }.onFailure { e ->
            logger.error(e) { "Failed to remove auto startup" }
        }
    }
}
