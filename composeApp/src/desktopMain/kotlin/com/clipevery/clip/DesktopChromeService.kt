package com.clipevery.clip

import com.clipevery.app.AppEnv
import com.clipevery.app.AppFileType
import com.clipevery.path.DesktopPathProvider
import com.clipevery.platform.currentPlatform
import org.openqa.selenium.Dimension
import org.openqa.selenium.OutputType
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.nio.file.Path
import java.util.Base64
import kotlin.io.path.absolutePathString

object DesktopChromeService: ChromeService {

    private const val CHROME_DRIVER = "chromedriver"

    private const val CHROME_HEADLESS_SHELL = "chrome-headless-shell"

    private val options: ChromeOptions = ChromeOptions()


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

    private var chromeDriver: ChromeDriver? = null


    init {
        val resourcesPath = DesktopPathProvider.resolve("resources", AppFileType.APP)
        val currentPlatform = currentPlatform()
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
        chromeDriver?.let{ driver ->
            val encodedContent = Base64.getEncoder().encodeToString(html.toByteArray())
            driver.get("data:text/html;base64,$encodedContent")
            driver.manage().window().size = Dimension(408, 120)
            val pageHeight = driver.executeScript("return document.body.scrollHeight") as Long
            val pageWidth = driver.executeScript("return document.body.scrollWidth") as Long
            driver.manage().window().size = Dimension(pageWidth.toInt() + 20, pageHeight.toInt())
            return driver.getScreenshotAs(OutputType.BYTES)
        } ?: run {
            return null
        }
    }
}