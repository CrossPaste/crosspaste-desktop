package com.clipevery.app

import com.clipevery.config.ConfigManager
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException
import java.nio.file.Paths
import java.util.Properties

val logger = KotlinLogging.logger {}

class DesktopAppInfoFactory(private val configManager: ConfigManager) : AppInfoFactory {
    override fun createAppInfo(): AppInfo {
        val appInstanceId = configManager.config.appInstanceId
        return AppInfo(appInstanceId = appInstanceId, appVersion = getVersion(), userName = getUserName())
    }
}

fun getVersion(): String {
    val properties = Properties()
    try {
        properties.load(
            Thread.currentThread().contextClassLoader
                .getResourceAsStream("version.properties"),
        )
        return properties.getProperty("version", "Unknown")
    } catch (e: IOException) {
        logger.error(e) { "Failed to read version" }
    }
    return "Unknown"
}

fun getUserName(): String {
    val userHome = System.getProperty("user.home")
    return Paths.get(userHome).toFile().name
}
