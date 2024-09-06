package com.crosspaste.path

import com.crosspaste.app.AppEnv
import com.crosspaste.platform.currentPlatform
import com.crosspaste.utils.DesktopResourceUtils
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getSystemProperty
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import kotlin.io.path.createTempDirectory

fun getPlatformPathProvider(): PlatformUserDataPathProvider {
    return if (AppEnv.CURRENT.isDevelopment()) {
        DevelopmentPlatformUserDataPathProvider()
    } else if (AppEnv.CURRENT.isTest()) {
        // In the test environment, DesktopPathProvider will be mocked
        TestPlatformUserDataPathProvider()
    } else {
        val platform = currentPlatform()
        if (platform.isWindows()) {
            WindowsPlatformUserDataPathProvider()
        } else if (platform.isMacos()) {
            MacosPlatformUserDataPathProvider()
        } else if (platform.isLinux()) {
            LinuxPlatformUserDataPathProvider()
        } else {
            throw IllegalStateException("Unknown platform: ${currentPlatform().name}")
        }
    }
}

class DevelopmentPlatformUserDataPathProvider : PlatformUserDataPathProvider {

    private val systemProperty = getSystemProperty()

    private val composeAppDir = systemProperty.get("user.dir")

    private val development = DesktopResourceUtils.loadProperties("development.properties")

    override fun getUserDefaultStoragePath(): Path {
        development.getProperty("pasteUserPath")?.let {
            val path = it.toPath(normalize = true)
            if (path.isAbsolute) {
                return path
            } else {
                return composeAppDir.toPath().resolve(it)
            }
        } ?: run {
            return composeAppDir.toPath()
        }
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
