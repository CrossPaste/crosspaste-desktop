package com.crosspaste.html

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crosspaste.app.AppWindowManager
import com.crosspaste.os.windows.WinProcessUtils
import com.crosspaste.os.windows.WinProcessUtils.killProcessSet
import com.crosspaste.os.windows.WindowDpiHelper
import com.crosspaste.platform.currentPlatform
import com.crosspaste.utils.DesktopHtmlUtils.dataUrl
import com.crosspaste.utils.DesktopResourceUtils
import com.crosspaste.utils.Retry
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okio.Path
import org.openqa.selenium.Dimension
import org.openqa.selenium.OutputType
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeDriverService
import org.openqa.selenium.chrome.ChromeOptions
import java.util.Properties
import kotlin.math.max

class DesktopBrowseService(private val appWindowManager: AppWindowManager) : BrowseService {

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

    override var startSuccess: Boolean by mutableStateOf(false)

    init {
        initChromeDriver()
    }

    private fun initChromeDriver() {
        try {
            chromeDriverService = ChromeDriverService.createDefaultService()
            chromeDriver = ChromeDriver(chromeDriverService, options)
            startSuccess = true
        } catch (e: Exception) {
            logger.error(e) { "chromeDriver auto init fail" }
            val chromeDriverProperties =
                DesktopResourceUtils
                    .loadProperties("chrome-driver.properties")

            (
                loadModule(chromeDriverProperties, useMirror = false)
                    ?: loadModule(chromeDriverProperties, useMirror = true)
            )?.let {
                val chromeDriverPath = it.first
                val chromeHeadlessShellPath = it.second
                chromeDriverService = ChromeDriverService.createDefaultService()
                System.setProperty(
                    "webdriver.chrome.driver",
                    chromeDriverPath.toString(),
                )
                options.setBinary(chromeHeadlessShellPath.toFile())
                chromeDriver = ChromeDriver(chromeDriverService, options)
                startSuccess = true
            }
        }
    }

    private fun loadModule(
        chromeDriverProperties: Properties,
        useMirror: Boolean,
    ): Pair<Path, Path>? {
        val chromeServiceModule = ChromeServiceServiceModule(chromeDriverProperties, useMirror)
        val loaderConfigs = chromeServiceModule.getModuleLoaderConfigs()

        loaderConfigs[CHROME_DRIVER]?.let { driver ->
            loaderConfigs[CHROME_HEADLESS_SHELL]?.let { chromeHeadlessShell ->
                val loader = ChromeModuleLoader()
                loader.load(driver)?.let { chromeDriverPath ->
                    loader.load(chromeHeadlessShell)?.let { chromeHeadlessShellPath ->
                        return Pair(chromeDriverPath, chromeHeadlessShellPath)
                    }
                }
            }
        }
        return null
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
