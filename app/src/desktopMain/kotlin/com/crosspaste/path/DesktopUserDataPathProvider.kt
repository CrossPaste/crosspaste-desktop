package com.crosspaste.path

import com.crosspaste.config.DevConfig
import com.crosspaste.platform.Platform
import com.crosspaste.utils.getAppEnvUtils
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getSystemProperty
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import kotlin.io.path.createTempDirectory

fun getPlatformPathProvider(platform: Platform): PlatformUserDataPathProvider {
    val appEnvUtils = getAppEnvUtils()
    return if (appEnvUtils.isDevelopment()) {
        DevelopmentPlatformUserDataPathProvider()
    } else if (appEnvUtils.isTest()) {
        // In the test environment, DesktopPathProvider will be mocked
        TestPlatformUserDataPathProvider()
    } else {
        if (platform.isWindows()) {
            WindowsPlatformUserDataPathProvider()
        } else if (platform.isMacos()) {
            MacosPlatformUserDataPathProvider()
        } else if (platform.isLinux()) {
            LinuxPlatformUserDataPathProvider()
        } else {
            throw IllegalStateException("Unknown platform: ${platform.name}")
        }
    }
}

class DevelopmentPlatformUserDataPathProvider : PlatformUserDataPathProvider {

    private val systemProperty = getSystemProperty()

    private val composeAppDir = systemProperty.get("user.dir")

    override fun getUserDefaultStoragePath(): Path =
        DevConfig.pasteUserPath?.let {
            val path = it.toPath(normalize = true)
            if (path.isAbsolute) {
                path
            } else {
                composeAppDir.toPath().resolve(it)
            }
        } ?: composeAppDir.toPath()
}

class TestPlatformUserDataPathProvider : PlatformUserDataPathProvider {

    private val tempDir = createTempDirectory(".crosspaste")

    override fun getUserDefaultStoragePath(): Path = tempDir.toOkioPath(normalize = true)
}

class WindowsPlatformUserDataPathProvider : PlatformUserDataPathProvider {

    private val systemProperty = getSystemProperty()

    private val userHome: Path = systemProperty.get("user.home").toPath(normalize = true)

    override fun getUserDefaultStoragePath(): Path = userHome.resolve(".crosspaste")
}

class MacosPlatformUserDataPathProvider : PlatformUserDataPathProvider {

    private val systemProperty = getSystemProperty()

    private val fileUtils = getFileUtils()

    private val userHome: Path = systemProperty.get("user.home").toPath(normalize = true)

    override fun getUserDefaultStoragePath(): Path {
        val appSupportPath =
            userHome
                .resolve("Library")
                .resolve("Application Support")
                .resolve("CrossPaste")
        if (!fileUtils.existFile(appSupportPath)) {
            fileUtils.createDir(appSupportPath)
        }
        return appSupportPath
    }
}

class LinuxPlatformUserDataPathProvider : PlatformUserDataPathProvider {

    private val systemProperty = getSystemProperty()

    private val userHome: Path = systemProperty.get("user.home").toPath(normalize = true)

    override fun getUserDefaultStoragePath(): Path = userHome.resolve(".local").resolve("shard").resolve(".crosspaste")
}
