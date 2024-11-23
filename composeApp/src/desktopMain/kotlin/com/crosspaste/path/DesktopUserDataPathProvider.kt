package com.crosspaste.path

import com.crosspaste.platform.getPlatform
import com.crosspaste.utils.DesktopResourceUtils
import com.crosspaste.utils.getAppEnvUtils
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getSystemProperty
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import kotlin.io.path.createTempDirectory

fun getPlatformPathProvider(): PlatformUserDataPathProvider {
    val appEnvUtils = getAppEnvUtils()
    return if (appEnvUtils.isDevelopment()) {
        DevelopmentPlatformUserDataPathProvider()
    } else if (appEnvUtils.isTest()) {
        // In the test environment, DesktopPathProvider will be mocked
        TestPlatformUserDataPathProvider()
    } else {
        val platform = getPlatform()
        if (platform.isWindows()) {
            WindowsPlatformUserDataPathProvider()
        } else if (platform.isMacos()) {
            MacosPlatformUserDataPathProvider()
        } else if (platform.isLinux()) {
            LinuxPlatformUserDataPathProvider()
        } else {
            throw IllegalStateException("Unknown platform: ${getPlatform().name}")
        }
    }
}

class DevelopmentPlatformUserDataPathProvider : PlatformUserDataPathProvider {

    private val systemProperty = getSystemProperty()

    private val composeAppDir = systemProperty.get("user.dir")

    private val development = DesktopResourceUtils.loadProperties("development.properties")

    override fun getUserDefaultStoragePath(): Path {
        return development.getProperty("pasteUserPath")?.let {
            val path = it.toPath(normalize = true)
            if (path.isAbsolute) {
                path
            } else {
                composeAppDir.toPath().resolve(it)
            }
        } ?: composeAppDir.toPath()
    }
}

class TestPlatformUserDataPathProvider : PlatformUserDataPathProvider {

    private val tempDir = createTempDirectory(".crosspaste")

    override fun getUserDefaultStoragePath(): Path {
        return tempDir.toOkioPath(normalize = true)
    }
}

class WindowsPlatformUserDataPathProvider : PlatformUserDataPathProvider {

    private val systemProperty = getSystemProperty()

    private val userHome: Path = systemProperty.get("user.home").toPath(normalize = true)

    override fun getUserDefaultStoragePath(): Path {
        return userHome.resolve(".crosspaste")
    }
}

class MacosPlatformUserDataPathProvider : PlatformUserDataPathProvider {

    private val systemProperty = getSystemProperty()

    private val fileUtils = getFileUtils()

    private val userHome: Path = systemProperty.get("user.home").toPath(normalize = true)

    override fun getUserDefaultStoragePath(): Path {
        val appSupportPath =
            userHome.resolve("Library")
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

    override fun getUserDefaultStoragePath(): Path {
        return userHome.resolve(".local").resolve("shard").resolve(".crosspaste")
    }
}
