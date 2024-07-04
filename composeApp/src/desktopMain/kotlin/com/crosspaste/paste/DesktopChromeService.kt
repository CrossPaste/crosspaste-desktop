package com.crosspaste.paste

import com.crosspaste.app.AppEnv
import com.crosspaste.app.AppWindowManager
import com.crosspaste.os.windows.WinProcessUtils
import com.crosspaste.os.windows.WinProcessUtils.killProcessSet
import com.crosspaste.os.windows.WindowDpiHelper
import com.crosspaste.path.DesktopPathProvider
import com.crosspaste.platform.currentPlatform
import com.crosspaste.utils.HtmlUtils.dataUrl
import com.crosspaste.utils.Retry
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.openqa.selenium.Dimension
import org.openqa.selenium.OutputType
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeDriverService
import org.openqa.selenium.chrome.ChromeOptions
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.math.max

class DesktopChromeService(private val appWindowManager: AppWindowManager) : ChromeService {

    companion object {
        private const val CHROME_DRIVER = "chromedriver"

        private const val CHROME_HEADLESS_SHELL = "chrome-headless-shell"

        private val logger = KotlinLogging.logger {}
    }

    private val currentPlatform = currentPlatform()

    private val scale: Double =
        if (currentPlatform.isWindows()) {
            val maxDpi: Int = WindowDpiHelper.getMaxDpiForMonitor()
            maxDpi / 96.0
        } else {
            1.0
        }

    private val baseOptions: ChromeOptions =
        ChromeOptions()
            .addArguments("--hide-scrollbars")
            .addArguments("--disable-extensions")
            .addArguments("--headless")
            .addArguments("--disable-gpu")
            .addArguments("--disable-software-rasterizer")
            .addArguments("--no-sandbox")

    private val options: ChromeOptions =
        if (currentPlatform.isWindows()) {
            baseOptions
                .addArguments("--force-device-scale-factor=$scale")
                .addArguments("--high-dpi-support=$scale")
        } else {
            baseOptions
        }

    private val initChromeDriver: (String, String, String, Path) -> Unit = { chromeSuffix, driverName, headlessName, resourcesPath ->
        val chromeDriverFile =
            File(
                resourcesPath
                    .resolve("$CHROME_DRIVER-$chromeSuffix")
                    .resolve(driverName)
                    .absolutePathString(),
            )

        val chromeHeadlessShellFile =
            File(
                resourcesPath
                    .resolve("$CHROME_HEADLESS_SHELL-$chromeSuffix")
                    .resolve(headlessName)
                    .absolutePathString(),
            )

        if (!chromeDriverFile.canExecute()) {
            chromeDriverFile.setExecutable(true)
        }

        if (!chromeHeadlessShellFile.canExecute()) {
            chromeHeadlessShellFile.setExecutable(true)
        }

        System.setProperty(
            "webdriver.chrome.driver",
            chromeDriverFile.absolutePath,
        )
        options.setBinary(chromeHeadlessShellFile.absolutePath)
    }

    private val windowDimension: Dimension =
        run {
            val detailViewDpSize = appWindowManager.searchWindowDetailViewDpSize
            val htmlWidthValue = detailViewDpSize.width.value - 20.0
            val htmlHeightValue = detailViewDpSize.height.value - 20.0
            if (currentPlatform.isWindows()) {
                val width: Int = (htmlWidthValue * scale).toInt()
                val height: Int = (htmlHeightValue * scale).toInt()
                Dimension(width, height)
            } else {
                Dimension(htmlWidthValue.toInt(), htmlHeightValue.toInt())
            }
        }

    private var chromeDriverService: ChromeDriverService? = null

    private var chromeDriver: ChromeDriver? = null

    init {
        initChromeDriver()
    }

    private fun initChromeDriver() {
        val resourcesPath =
            if (AppEnv.CURRENT.isDevelopment()) {
                DesktopPathProvider.pasteAppJarPath.resolve("resources")
            } else {
                DesktopPathProvider.pasteAppJarPath
            }

        // todo not support 32 bit OS

        if (currentPlatform.isMacos()) {
            if (currentPlatform.arch.contains("x86_64")) {
                val macX64ResourcesPath =
                    if (AppEnv.CURRENT.isDevelopment()) resourcesPath.resolve("macos-x64") else resourcesPath
                initChromeDriver.invoke("mac-x64", "chromedriver", "chrome-headless-shell", macX64ResourcesPath)
            } else {
                val macArm64ResourcesPath =
                    if (AppEnv.CURRENT.isDevelopment()) resourcesPath.resolve("macos-arm64") else resourcesPath
                initChromeDriver.invoke("mac-arm64", "chromedriver", "chrome-headless-shell", macArm64ResourcesPath)
            }
        } else if (currentPlatform.isWindows()) {
            if (currentPlatform.is64bit()) {
                val win64ResourcesPath =
                    if (AppEnv.CURRENT.isDevelopment()) resourcesPath.resolve("windows-x64") else resourcesPath
                initChromeDriver.invoke("win64", "chromedriver.exe", "chrome-headless-shell.exe", win64ResourcesPath)
            }
        } else if (currentPlatform.isLinux()) {
            if (currentPlatform.is64bit()) {
                val linux64ResourcesPath =
                    if (AppEnv.CURRENT.isDevelopment()) resourcesPath.resolve("linux-x64") else resourcesPath
                initChromeDriver.invoke("linux64", "chromedriver", "chrome-headless-shell", linux64ResourcesPath)
            }
        }

        chromeDriverService = ChromeDriverService.createDefaultService()

        chromeDriver = ChromeDriver(chromeDriverService, options)
    }

    @Synchronized
    override fun html2Image(html: String): ByteArray? {
        return Retry.retry(1, {
            doHtml2Image(html)
        }) {
            quit()
            initChromeDriver()
        }
    }

    private val quitSupervisorJob = SupervisorJob()

    private val quitScope = CoroutineScope(ioDispatcher + quitSupervisorJob)

    override fun quit() {
        chromeDriver?.let { driver ->
            if (currentPlatform.isWindows()) {
                val shellPids = collectChromeHeadlessShellProcessIds()

                val deferred =
                    quitScope.async {
                        driver.quit()
                        true
                    }

                runBlocking {
                    try {
                        val result =
                            withTimeoutOrNull(1000) {
                                deferred.await()
                            }
                        if (result == null) {
                            killProcessSet(shellPids)
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "chromeDriver & chromeHeadlessShell quit fail" }
                    }
                }
            } else {
                driver.quit()
            }
        }
    }

    private fun collectChromeHeadlessShellProcessIds(): Set<Long> {
        val pid = ProcessHandle.current().pid()
        val chromeHeadlessSHellProcessIds = mutableSetOf<Long>()

        val chromeDriverPid =
            WinProcessUtils.getChildProcessIds(pid)
                .firstOrNull { it.first == "chromedriver.exe" }?.second

        if (chromeDriverPid == null) {
            return chromeHeadlessSHellProcessIds
        }

        val currentPids = mutableSetOf<Long>()

        currentPids.add(chromeDriverPid)

        while (currentPids.isNotEmpty()) {
            val shellPids =
                currentPids.map { parentPid ->
                    WinProcessUtils.getChildProcessIds(parentPid)
                        .filter { it.first == "chrome-headless-shell.exe" }
                        .map { it.second }
                }.flatten().toSet()

            chromeHeadlessSHellProcessIds.addAll(shellPids)
            currentPids.clear()
            currentPids.addAll(shellPids)
        }

        return chromeHeadlessSHellProcessIds
    }

    @Suppress("UNCHECKED_CAST")
    private fun doHtml2Image(html: String): ByteArray? {
        chromeDriver?.let { driver ->
            driver.get(dataUrl(html))
            driver.manage().window().size = windowDimension
            val dimensions: List<Long> = driver.executeScript(JsCode.CODE) as List<Long>
            val pageWidth = max(dimensions[0].toInt(), windowDimension.width)
            val pageHeight = max(dimensions[1].toInt(), windowDimension.height)
            driver.manage().window().size = Dimension(pageWidth, pageHeight)
            return driver.getScreenshotAs(OutputType.BYTES)
        } ?: run {
            return null
        }
    }
}

object JsCode {
    const val CODE: String = """
    const body = document.body;
    const children = Array.from(body.children);
    const backgroundColors = children.map(child => getComputedStyle(child).backgroundColor);
    const allSameBackgroundColor = backgroundColors.every(color => color === backgroundColors[0]);
    if (children.length > 1 && allSameBackgroundColor) {
        body.style.backgroundColor = backgroundColors[0];
    } else if (children.length === 1) {
        const firstChildStyle = getComputedStyle(children[0]);
        if (firstChildStyle.backgroundColor && firstChildStyle.color) {
            body.style.backgroundColor = firstChildStyle.backgroundColor;
            body.style.color = firstChildStyle.color;
        }
    }
    return [document.body.scrollWidth, document.body.scrollHeight]
    """
}
