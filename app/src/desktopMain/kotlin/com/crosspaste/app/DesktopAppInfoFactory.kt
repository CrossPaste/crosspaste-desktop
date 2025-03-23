package com.crosspaste.app

import com.crosspaste.config.ConfigManager
import com.crosspaste.utils.getAppEnvUtils
import com.crosspaste.utils.getSystemProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Paths
import java.util.Properties

class DesktopAppInfoFactory(private val configManager: ConfigManager) : AppInfoFactory {

    private val logger = KotlinLogging.logger {}

    private val systemProperty = getSystemProperty()

    private val properties: Properties? =
        runCatching {
            val properties = Properties()
            properties.load(
                Thread.currentThread().contextClassLoader
                    .getResourceAsStream("crosspaste-version.properties"),
            )
            properties
        }.onFailure { e ->
            logger.error(e) { "Failed to read version" }
        }.getOrNull()

    override fun createAppInfo(): AppInfo {
        val appInstanceId = configManager.getCurrentConfig().appInstanceId
        return AppInfo(
            appInstanceId = appInstanceId,
            appVersion = getVersion(),
            appRevision = getRevision(),
            userName = getUserName(),
        )
    }

    override fun getVersion(): String {
        return getVersion(appEnvUtils.getCurrentAppEnv(), properties)
    }

    override fun getRevision(): String {
        return properties?.getProperty("revision", "Unknown") ?: "Unknown"
    }

    override fun getUserName(): String {
        val userHome = systemProperty.get("user.home")
        return Paths.get(userHome).toFile().name
    }

    companion object {

        private val appEnvUtils = getAppEnvUtils()

        fun getVersion(
            appEnv: AppEnv,
            properties: Properties?,
        ): String {
            val version = properties?.getProperty("version", "Unknown") ?: return "Unknown"

            return when (appEnv) {
                AppEnv.DEVELOPMENT -> "$version-dev"
                AppEnv.TEST -> "$version-test"
                else -> {
                    val beta =
                        if (appEnv == AppEnv.BETA) {
                            "-beta"
                        } else {
                            ""
                        }
                    properties.getProperty("prerelease")?.let { prerelease ->
                        "$version$beta-$prerelease"
                    } ?: "$version$beta"
                }
            }
        }
    }
}
