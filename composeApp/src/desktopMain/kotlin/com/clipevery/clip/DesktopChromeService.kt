package com.clipevery.clip

import com.clipevery.app.AppEnv
import com.clipevery.app.AppFileType
import com.clipevery.app.AppWindowManager
import com.clipevery.os.windows.WindowDpiHelper
import com.clipevery.path.DesktopPathProvider
import com.clipevery.platform.currentPlatform
import com.clipevery.utils.HtmlUtils.dataUrl
import com.clipevery.utils.Retry
import org.openqa.selenium.Dimension
import org.openqa.selenium.OutputType
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.math.max

class DesktopChromeService(private val appWindowManager: AppWindowManager) : ChromeService {

    companion object {
        private const val CHROME_DRIVER = "chromedriver"

        private const val CHROME_HEADLESS_SHELL = "chrome-headless-shell"
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

    private var chromeDriver: ChromeDriver? = null

    init {
        initChromeDriver()
    }

    private fun initChromeDriver() {
        val resourcesPath = DesktopPathProvider.resolve("resources", AppFileType.APP)
        if (currentPlatform.isMacos()) {
            if (currentPlatform.arch.contains("x86_64")) {
                val macX64ResourcesPath =
                    if (AppEnv.isDevelopment()) resourcesPath.resolve("macos-x64") else resourcesPath
                initChromeDriver.invoke("mac-x64", "chromedriver", "chrome-headless-shell", macX64ResourcesPath)
            } else {
                val macArm64ResourcesPath =
                    if (AppEnv.isDevelopment()) resourcesPath.resolve("macos-arm64") else resourcesPath
                initChromeDriver.invoke("mac-arm64", "chromedriver", "chrome-headless-shell", macArm64ResourcesPath)
            }
        } else if (currentPlatform.isWindows()) {
            if (currentPlatform.is64bit()) {
                val win64ResourcesPath =
                    if (AppEnv.isDevelopment()) resourcesPath.resolve("windows-x64") else resourcesPath
                initChromeDriver.invoke("win64", "chromedriver.exe", "chrome-headless-shell.exe", win64ResourcesPath)
            } else {
                val win32ResourcesPath =
                    if (AppEnv.isDevelopment()) resourcesPath.resolve("windows-x86") else resourcesPath
                initChromeDriver.invoke("win32", "chromedriver.exe", "chrome-headless-shell.exe", win32ResourcesPath)
            }
        } else if (currentPlatform.isLinux()) {
            // todo linux init chrome driver
        }

        chromeDriver = ChromeDriver(options)
    }

    @Synchronized
    override fun html2Image(html: String): ByteArray? {
        return Retry.retry(1, {
            doHtml2Image(html)
        }) {
            chromeDriver?.quit()
            initChromeDriver()
        }
    }

    override fun quit() {
        chromeDriver?.quit()
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
