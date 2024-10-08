package com.crosspaste.html

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crosspaste.app.AppSize
import com.crosspaste.html.ChromeServiceServiceModule.Companion.CHROME_DRIVER_MODULE_ITEM_NAME
import com.crosspaste.html.ChromeServiceServiceModule.Companion.CHROME_HEADLESS_SHELL_MODULE_ITEM_NAME
import com.crosspaste.module.ModuleLoaderConfig
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.getPlatform
import com.crosspaste.platform.windows.WinProcessUtils
import com.crosspaste.platform.windows.WinProcessUtils.killProcessSet
import com.crosspaste.platform.windows.WindowDpiHelper
import com.crosspaste.presist.FilePersist
import com.crosspaste.utils.DesktopResourceUtils
import com.crosspaste.utils.Retry
import com.crosspaste.utils.getHtmlUtils
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
import kotlin.math.max

class DesktopHtmlRenderingService(
    private val appSize: AppSize,
    private val filePersist: FilePersist,
    private val userDataPathProvider: UserDataPathProvider,
) : HtmlRenderingService {

    private val logger = KotlinLogging.logger {}

    private val htmlUtils = getHtmlUtils()

    private val currentPlatform = getPlatform()

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
            val detailViewDpSize = appSize.searchWindowDetailViewDpSize
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

    private val chromeDriverProperties =
        DesktopResourceUtils
            .loadProperties("chrome-driver.properties")

    init {
        initChromeDriver()
    }

    private fun initChromeDriver() {
        val chromeModuleLoader = ChromeModuleLoader(userDataPathProvider)
        val chromeServiceModule = ChromeServiceServiceModule(chromeDriverProperties)

        val optLoaderConfig = chromeServiceModule.getModuleLoaderConfig()

        try {
            optLoaderConfig?.let { loaderConfig ->
                if (chromeModuleLoader.load(loaderConfig)) {
                    startByLoaderModule(loaderConfig)
                    startSuccess = true
                    return
                }
            }
        } catch (e: Exception) {
            startSuccess = false
            return
        }
    }

    private fun startByLoaderModule(moduleLoaderConfig: ModuleLoaderConfig): Boolean {
        val installPath = moduleLoaderConfig.installPath
        moduleLoaderConfig.getModuleItem(CHROME_DRIVER_MODULE_ITEM_NAME)?.let { chromeDriverModule ->
            moduleLoaderConfig.getModuleItem(CHROME_HEADLESS_SHELL_MODULE_ITEM_NAME)?.let { chromeHeadlessShellModule ->
                val chromeDriverFile = chromeDriverModule.getModuleFilePath(installPath).toFile()
                val chromeHeadlessShellFile = chromeHeadlessShellModule.getModuleFilePath(installPath).toFile()

                if (!chromeDriverFile.canExecute()) {
                    chromeDriverFile.setExecutable(true)
                }

                if (!chromeHeadlessShellFile.canExecute()) {
                    chromeHeadlessShellFile.setExecutable(true)
                }

                chromeDriverService = ChromeDriverService.createDefaultService()
                System.setProperty(
                    "webdriver.chrome.driver",
                    chromeDriverFile.absolutePath,
                )
                options.setBinary(chromeHeadlessShellFile)
                chromeDriver = ChromeDriver(chromeDriverService, options)
                return true
            }
        }
        return false
    }

    @Synchronized
    override fun saveRenderImage(
        html: String,
        savePath: Path,
    ) {
        return Retry.retry(1, {
            html2Image(html)?.let { bytes ->
                filePersist.createOneFilePersist(savePath).saveBytes(bytes)
            }
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
    private fun html2Image(html: String): ByteArray? {
        chromeDriver?.let { driver ->
            driver.get(htmlUtils.dataUrl(html))
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
