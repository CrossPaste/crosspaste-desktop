package com.crosspaste.rendering

import com.crosspaste.module.ModuleLoaderConfig
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.getPlatform
import com.crosspaste.platform.windows.WinProcessUtils
import com.crosspaste.platform.windows.WinProcessUtils.killProcessSet
import com.crosspaste.presist.FilePersist
import com.crosspaste.rendering.ChromeServiceServiceModule.Companion.CHROME_DRIVER_MODULE_ITEM_NAME
import com.crosspaste.rendering.ChromeServiceServiceModule.Companion.CHROME_HEADLESS_SHELL_MODULE_ITEM_NAME
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
    private val filePersist: FilePersist,
    private val renderingHelper: RenderingHelper,
    private val userDataPathProvider: UserDataPathProvider,
) : RenderingService<String> {

    private val logger = KotlinLogging.logger {}

    private val currentPlatform = getPlatform()

    private val htmlUtils = getHtmlUtils()

    private val baseOptions: ChromeOptions =
        ChromeOptions()
            .addArguments("--hide-scrollbars")
            .addArguments("--disable-extensions")
            .addArguments("--headless")
            .addArguments("--disable-gpu")
            .addArguments("--disable-software-rasterizer")
            .addArguments("--no-sandbox")

    private var chromeDriverService: ChromeDriverService? = null

    private var chromeDriver: ChromeDriver? = null

    private val chromeDriverProperties =
        DesktopResourceUtils
            .loadProperties("chrome-driver.properties")

    override fun start() {
        val chromeModuleLoader = ChromeModuleLoader(userDataPathProvider)
        val chromeServiceModule = ChromeServiceServiceModule(chromeDriverProperties)

        val optLoaderConfig = chromeServiceModule.getModuleLoaderConfig()

        runCatching {
            optLoaderConfig?.let { loaderConfig ->
                val loadSuccess = runBlocking { chromeModuleLoader.load(loaderConfig) }
                if (loadSuccess) {
                    startByLoaderModule(loaderConfig)
                    logger.info { "chromeDriver & chromeHeadlessShell start success" }
                }
            } ?: run {
                logger.warn { "chromeDriver & chromeHeadlessShell not found" }
            }
        }.onFailure { e ->
            logger.error(e) { "chromeDriver & chromeHeadlessShell start fail" }
        }
    }

    private fun buildOptions(): ChromeOptions {
        return if (currentPlatform.isWindows()) {
            val scale = renderingHelper.scale
            baseOptions
                .addArguments("--force-device-scale-factor=$scale")
                .addArguments("--high-dpi-support=$scale")
        } else {
            baseOptions
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
                val options = buildOptions()
                options.setBinary(chromeHeadlessShellFile)
                chromeDriver = ChromeDriver(chromeDriverService, options)
                return true
            }
        }
        return false
    }

    override suspend fun saveRenderImage(
        input: String,
        savePath: Path,
    ) {
        return Retry.retry(1, {
            html2Image(input)?.let { bytes ->
                filePersist.createOneFilePersist(savePath).saveBytes(bytes)
            }
        }) {
            restart()
        }
    }

    private val quitSupervisorJob = SupervisorJob()

    private val quitScope = CoroutineScope(ioDispatcher + quitSupervisorJob)

    override fun stop() {
        chromeDriver?.let { driver ->
            if (currentPlatform.isWindows()) {
                val shellPids = collectChromeHeadlessShellProcessIds()

                val deferred =
                    quitScope.async {
                        driver.quit()
                        true
                    }

                runBlocking {
                    runCatching {
                        val result =
                            withTimeoutOrNull(1000) {
                                deferred.await()
                            }
                        if (result == null) {
                            killProcessSet(shellPids)
                        }
                    }.onFailure { e ->
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
        return chromeDriver?.let { driver ->
            driver.get(htmlUtils.dataUrl(html))
            val windowDimension =
                renderingHelper.dimension.let {
                    Dimension(it.width, it.height)
                }
            driver.manage().window().size = windowDimension
            val dimensions: List<Long> = driver.executeScript(JsCode.CODE) as List<Long>
            val pageWidth = max(dimensions[0].toInt(), windowDimension.width)
            val pageHeight = max(dimensions[1].toInt(), windowDimension.height)
            driver.manage().window().size = Dimension(pageWidth, pageHeight)
            driver.getScreenshotAs(OutputType.BYTES)
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
