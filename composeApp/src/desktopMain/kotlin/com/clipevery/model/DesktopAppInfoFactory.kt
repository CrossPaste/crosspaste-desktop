package com.clipevery.model

import com.clipevery.AppInfoFactory
import com.clipevery.platform.currentPlatform
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException
import java.nio.file.Paths
import java.util.Properties


val logger = KotlinLogging.logger {}

fun getAppInfoFactory(): AppInfoFactory {
    val platform = currentPlatform()
    return if (platform.isMacos()) {
        MacosAppInfoFactory()
    } else if (platform.isWindows()) {
        WindowsAppInfoFactory()
    } else {
        throw IllegalStateException("Unknown platform: ${platform.name}")
    }
}

class MacosAppInfoFactory: AppInfoFactory {
    override fun createAppInfo(): AppInfo {
        return AppInfo(appVersion = getVersion(), userName = getUserName())
    }
}

class WindowsAppInfoFactory: AppInfoFactory {
    override fun createAppInfo(): AppInfo {
        return AppInfo(appVersion = getVersion(), userName = getUserName())
    }
}

fun getVersion(): String {
    val properties = Properties()
    try {
        properties.load( Thread.currentThread().contextClassLoader
            .getResourceAsStream("version.properties"))
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