package com.clipevery.clip

import com.clipevery.app.AppEnv
import com.clipevery.app.AppFileType
import com.clipevery.os.windows.WindowDpiHelper
import com.clipevery.path.DesktopPathProvider
import com.clipevery.platform.currentPlatform
import com.clipevery.utils.HtmlUtils.dataUrl
import com.clipevery.utils.Retry
import org.openqa.selenium.Dimension
import org.openqa.selenium.OutputType
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.math.max

object DesktopChromeService: ChromeService {

    private const val CHROME_DRIVER = "chromedriver"

    private const val CHROME_HEADLESS_SHELL = "chrome-headless-shell"

    private val currentPlatform = currentPlatform()

    private val options: ChromeOptions = ChromeOptions()
        .addArguments("--hide-scrollbars")
        .addArguments("--disable-extensions")
        .addArguments("--headless")
        .addArguments("--disable-gpu")
        .addArguments("--disable-software-rasterizer")
        .addArguments("--no-sandbox")

    private val initChromeDriver: (String, String, String, Path) -> Unit = { chromeSuffix, driverName, headlessName, resourcesPath ->
        System.setProperty("webdriver.chrome.driver", resourcesPath
            .resolve("$CHROME_DRIVER-$chromeSuffix")
            .resolve(driverName)
            .absolutePathString())
        options.setBinary(resourcesPath
            .resolve("$CHROME_HEADLESS_SHELL-$chromeSuffix")
            .resolve(headlessName)
            .absolutePathString())
    }

    private val windowDimension: Dimension  = if (currentPlatform.isWindows()) {
        val maxDpi: Int = WindowDpiHelper.getMaxDpiForMonitor()
        val width: Int = ((340.0 * maxDpi) / 96.0).toInt()
        val height: Int = ((100.0 * maxDpi) / 96.0).toInt()
        Dimension(width, height)
    } else {
        Dimension(340, 100)
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
        } else  if (currentPlatform.isLinux()) {
            // todo linux init chrome driver
        }

        chromeDriver = ChromeDriver(options)
    }

    @Synchronized
    override fun html2Image(html: String): ByteArray? {
        return Retry.retry(3, {
            doHtml2Image(html)
        }) {
            chromeDriver?.quit()
            initChromeDriver()
        }
    }

    private fun doHtml2Image(html: String): ByteArray? {
        chromeDriver?.let{ driver ->
            driver.get(dataUrl(html))
            driver.manage().window().size = windowDimension
            val dimensions: List<Long> = driver.executeScript("return [document.body.scrollWidth, document.body.scrollHeight]") as List<Long>
            val pageWidth = max(dimensions[0].toInt(), windowDimension.width)
            val pageHeight = max(dimensions[1].toInt(), windowDimension.height)
            driver.manage().window().size = Dimension(pageWidth, pageHeight)
            return driver.getScreenshotAs(OutputType.BYTES)
        } ?: run {
            return null
        }
    }
}