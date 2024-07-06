package com.crosspaste.app

import com.crosspaste.config.ConfigManager
import com.crosspaste.utils.getSystemProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException
import java.nio.file.Paths
import java.util.*

val logger = KotlinLogging.logger {}

class DesktopAppInfoFactory(private val configManager: ConfigManager) : AppInfoFactory {

    private val systemProperty = getSystemProperty()

    private val properties: Properties? =
        run {
            try {
                val properties = Properties()
                properties.load(
                    Thread.currentThread().contextClassLoader
                        .getResourceAsStream("crosspaste-version.properties"),
                )
                properties
            } catch (e: IOException) {
                logger.error(e) { "Failed to read version" }
                null
            }
        }

    override fun createAppInfo(): AppInfo {
        val appInstanceId = configManager.config.appInstanceId
        return AppInfo(
            appInstanceId = appInstanceId,
            appVersion = getVersion(),
            appRevision = getRevision(),
            userName = getUserName(),
        )
    }

    override fun getVersion(): String {
        return getVersion(AppEnv.CURRENT) {
            properties
        }
    }

    override fun getRevision(): String {
        return properties?.getProperty("revision", "Unknown") ?: "Unknown"
    }

    override fun getUserName(): String {
        val userHome = systemProperty.get("user.home")
        return Paths.get(userHome).toFile().name
    }

    companion object {

        fun getVersion(
            appEnv: AppEnv = AppEnv.PRODUCTION,
            load: () -> Properties?,
        ): String {
            return load()?.let { properties ->
                val version = properties.getProperty("version", "Unknown")

                if (appEnv.isDevelopment()) {
                    "$version-dev"
                } else if (appEnv.isTest()) {
                    "$version-test"
                } else {
                    val beta: String? = properties.getProperty("beta")

                    val betaSuffix =
                        if (beta == "0") {
                            "-beta"
                        } else if (beta != null) {
                            "-beta$beta"
                        } else {
                            ""
                        }
                    "$version$betaSuffix"
                }
            } ?: "Unknown"
        }
    }
}
