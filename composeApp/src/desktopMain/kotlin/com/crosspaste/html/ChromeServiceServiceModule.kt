package com.crosspaste.html

import com.crosspaste.app.AppFileType
import com.crosspaste.module.ModuleLoaderConfig
import com.crosspaste.module.ServiceModule
import com.crosspaste.path.DesktopAppPathProvider
import com.crosspaste.platform.currentPlatform
import java.util.Properties

class ChromeServiceServiceModule(
    private val properties: Properties,
    private val useMirror: Boolean = false,
) : ServiceModule {

    companion object {
        const val CHROME_SERVICE_MODULE_NAME = "ChromeService"
        const val CHROME_DRIVER_MODULE_NAME = "chromedriver"
        const val CHROME_HEADLESS_SHELL_MODULE_NAME = "chrome-headless-shell"

        const val DEFAULT_HOST = "https://storage.googleapis.com/chrome-for-testing-public"
        const val MIRROR_HOST = "https://cdn.npmmirror.com/binaries/chrome-for-testing"
    }

    private val platform = currentPlatform()

    private val appPathProvider = DesktopAppPathProvider

    override val serviceName: String = CHROME_SERVICE_MODULE_NAME

    override val moduleNames: List<String> =
        listOf(
            CHROME_DRIVER_MODULE_NAME,
            CHROME_HEADLESS_SHELL_MODULE_NAME,
        )

    private fun buildUrl(path: String): String {
        val host = if (useMirror) MIRROR_HOST else DEFAULT_HOST
        return "$host$path"
    }

    override fun getModuleLoaderConfigs(): Map<String, ModuleLoaderConfig> {
        val chromeDriverDir =
            appPathProvider.resolve(appFileType = AppFileType.MODULE)
                .resolve(CHROME_SERVICE_MODULE_NAME)
                .resolve(CHROME_DRIVER_MODULE_NAME)

        val chromeHeadlessShellDir =
            appPathProvider.resolve(appFileType = AppFileType.MODULE)
                .resolve(CHROME_SERVICE_MODULE_NAME)
                .resolve(CHROME_HEADLESS_SHELL_MODULE_NAME)

        if (platform.isWindows() && platform.is64bit()) {
            return mapOf(
                CHROME_DRIVER_MODULE_NAME to
                    ModuleLoaderConfig(
                        url = buildUrl(properties.getProperty("chromedriver-win64")),
                        installPath = chromeDriverDir.resolve("chromedriver.exe"),
                        sha256 = properties.getProperty("chromedriver-win64-sha256"),
                    ),
                CHROME_HEADLESS_SHELL_MODULE_NAME to
                    ModuleLoaderConfig(
                        url = buildUrl(properties.getProperty("chrome-headless-shell-win64")),
                        installPath = chromeHeadlessShellDir.resolve("chrome-headless-shell.exe"),
                        sha256 = properties.getProperty("chrome-headless-shell-win64-sha256"),
                    ),
            )
        } else if (platform.isMacos()) {
            return if (platform.arch.contains("x86_64")) {
                mapOf(
                    CHROME_DRIVER_MODULE_NAME to
                        ModuleLoaderConfig(
                            url = buildUrl(properties.getProperty("chromedriver-mac-x64")),
                            installPath = chromeDriverDir.resolve("chromedriver"),
                            sha256 = properties.getProperty("chromedriver-mac-x64-sha256"),
                        ),
                    CHROME_HEADLESS_SHELL_MODULE_NAME to
                        ModuleLoaderConfig(
                            url = buildUrl(properties.getProperty("chrome-headless-shell-mac-x64")),
                            installPath = chromeHeadlessShellDir.resolve("chrome-headless-shell"),
                            sha256 = properties.getProperty("chrome-headless-shell-mac-x64-sha256"),
                        ),
                )
            } else {
                mapOf(
                    CHROME_DRIVER_MODULE_NAME to
                        ModuleLoaderConfig(
                            url = buildUrl(properties.getProperty("chromedriver-mac-arm64")),
                            installPath = chromeDriverDir.resolve("chromedriver"),
                            sha256 = properties.getProperty("chromedriver-mac-arm64-sha256"),
                        ),
                    CHROME_HEADLESS_SHELL_MODULE_NAME to
                        ModuleLoaderConfig(
                            url = buildUrl(properties.getProperty("chrome-headless-shell-mac-arm64")),
                            installPath = chromeHeadlessShellDir.resolve("chrome-headless-shell"),
                            sha256 = properties.getProperty("chrome-headless-shell-mac-arm64-sha256"),
                        ),
                )
            }
        } else if (platform.isLinux() && platform.is64bit()) {
            return mapOf(
                CHROME_DRIVER_MODULE_NAME to
                    ModuleLoaderConfig(
                        url = buildUrl(properties.getProperty("chromedriver-linux64")),
                        installPath = chromeDriverDir.resolve("chromedriver"),
                        sha256 = properties.getProperty("chromedriver-linux64-sha256"),
                    ),
                CHROME_HEADLESS_SHELL_MODULE_NAME to
                    ModuleLoaderConfig(
                        url = buildUrl(properties.getProperty("chrome-headless-shell-linux64")),
                        installPath = chromeHeadlessShellDir.resolve("chrome-headless-shell"),
                        sha256 = properties.getProperty("chrome-headless-shell-linux64-sha256"),
                    ),
            )
        } else {
            return mapOf()
        }
    }
}
