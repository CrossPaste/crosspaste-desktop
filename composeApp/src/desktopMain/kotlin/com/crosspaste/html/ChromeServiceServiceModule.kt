package com.crosspaste.html

import com.crosspaste.app.AppFileType
import com.crosspaste.module.ModuleItem
import com.crosspaste.module.ModuleLoaderConfig
import com.crosspaste.module.ServiceModule
import com.crosspaste.path.DesktopAppPathProvider
import com.crosspaste.platform.currentPlatform
import java.util.Properties

class ChromeServiceServiceModule(
    private val properties: Properties,
) : ServiceModule {

    companion object {
        const val CHROME_SERVICE_MODULE_NAME = "ChromeService"
        const val CHROME_DRIVER_MODULE_ITEM_NAME = "chromedriver"
        const val CHROME_HEADLESS_SHELL_MODULE_ITEM_NAME = "chrome-headless-shell"

        const val DEFAULT_HOST = "https://storage.googleapis.com/chrome-for-testing-public"
        const val MIRROR_HOST = "https://cdn.npmmirror.com/binaries/chrome-for-testing"

        val CHROME_SERVICE_DIR =
            DesktopAppPathProvider.resolve(appFileType = AppFileType.MODULE)
                .resolve(CHROME_SERVICE_MODULE_NAME)
    }

    private val platform = currentPlatform()

    private val hosts = listOf(DEFAULT_HOST, MIRROR_HOST)

    override fun getModuleLoaderConfig(): ModuleLoaderConfig? {
        if (platform.isWindows() && platform.is64bit()) {
            return ModuleLoaderConfig(
                installPath = CHROME_SERVICE_DIR,
                moduleName = CHROME_SERVICE_MODULE_NAME,
                moduleItems =
                    listOf(
                        ModuleItem(
                            hosts = hosts,
                            path = properties.getProperty("chromedriver-win64"),
                            moduleItemName = CHROME_DRIVER_MODULE_ITEM_NAME,
                            relativePath = listOf("chromedriver-win64", "chromedriver.exe"),
                            sha256 = properties.getProperty("chromedriver-win64-sha256"),
                        ),
                        ModuleItem(
                            hosts = hosts,
                            path = properties.getProperty("chrome-headless-shell-win64"),
                            moduleItemName = CHROME_HEADLESS_SHELL_MODULE_ITEM_NAME,
                            relativePath = listOf("chrome-headless-shell-win64", "chrome-headless-shell.exe"),
                            sha256 = properties.getProperty("chrome-headless-shell-win64-sha256"),
                        ),
                    ),
            )
        } else if (platform.isMacos()) {
            return if (platform.arch.contains("x86_64")) {
                ModuleLoaderConfig(
                    installPath = CHROME_SERVICE_DIR,
                    moduleName = CHROME_SERVICE_MODULE_NAME,
                    moduleItems =
                        listOf(
                            ModuleItem(
                                hosts = hosts,
                                path = properties.getProperty("chromedriver-mac-x64"),
                                moduleItemName = CHROME_DRIVER_MODULE_ITEM_NAME,
                                relativePath = listOf("chromedriver-mac-x64", "chromedriver"),
                                sha256 = properties.getProperty("chromedriver-mac-x64-sha256"),
                            ),
                            ModuleItem(
                                hosts = hosts,
                                path = properties.getProperty("chrome-headless-shell-mac-x64"),
                                moduleItemName = CHROME_HEADLESS_SHELL_MODULE_ITEM_NAME,
                                relativePath = listOf("chrome-headless-shell-mac-x64", "chrome-headless-shell"),
                                sha256 = properties.getProperty("chrome-headless-shell-mac-x64-sha256"),
                            ),
                        ),
                )
            } else {
                return ModuleLoaderConfig(
                    installPath = CHROME_SERVICE_DIR,
                    moduleName = CHROME_SERVICE_MODULE_NAME,
                    moduleItems =
                        listOf(
                            ModuleItem(
                                hosts = hosts,
                                path = properties.getProperty("chromedriver-mac-arm64"),
                                moduleItemName = CHROME_DRIVER_MODULE_ITEM_NAME,
                                relativePath = listOf("chromedriver-mac-arm64", "chromedriver"),
                                sha256 = properties.getProperty("chromedriver-mac-arm64-sha256"),
                            ),
                            ModuleItem(
                                hosts = hosts,
                                path = properties.getProperty("chrome-headless-shell-mac-arm64"),
                                moduleItemName = CHROME_HEADLESS_SHELL_MODULE_ITEM_NAME,
                                relativePath = listOf("chrome-headless-shell-mac-arm64", "chrome-headless-shell"),
                                sha256 = properties.getProperty("chrome-headless-shell-mac-arm64-sha256"),
                            ),
                        ),
                )
            }
        } else if (platform.isLinux() && platform.is64bit()) {
            return ModuleLoaderConfig(
                installPath = CHROME_SERVICE_DIR,
                moduleName = CHROME_SERVICE_MODULE_NAME,
                moduleItems =
                    listOf(
                        ModuleItem(
                            hosts = hosts,
                            path = properties.getProperty("chromedriver-linux64"),
                            moduleItemName = CHROME_DRIVER_MODULE_ITEM_NAME,
                            relativePath = listOf("chromedriver-linux64", "chromedriver"),
                            sha256 = properties.getProperty("chromedriver-linux64-sha256"),
                        ),
                        ModuleItem(
                            hosts = hosts,
                            path = properties.getProperty("chrome-headless-shell-linux64"),
                            moduleItemName = CHROME_HEADLESS_SHELL_MODULE_ITEM_NAME,
                            relativePath = listOf("chrome-headless-shell-linux64", "chrome-headless-shell"),
                            sha256 = properties.getProperty("chrome-headless-shell-linux64-sha256"),
                        ),
                    ),
            )
        } else {
            return null
        }
    }
}
