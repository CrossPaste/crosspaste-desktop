package com.crosspaste.icon

import com.crosspaste.app.AppFileType
import com.crosspaste.os.macos.api.MacosApi
import com.crosspaste.os.windows.api.User32
import com.crosspaste.path.DesktopPathProvider
import com.crosspaste.path.PathProvider
import com.crosspaste.platform.currentPlatform
import com.crosspaste.utils.ConcurrentPlatformMap
import com.crosspaste.utils.PlatformLock
import com.crosspaste.utils.createConcurrentPlatformMap
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path

object DesktopFileExtLoader : ConcurrentLoader<String, Path>, FileExtIconLoader {

    private val logger = KotlinLogging.logger {}

    private val pathProvider: PathProvider = DesktopPathProvider

    override val lockMap: ConcurrentPlatformMap<Path, PlatformLock> = createConcurrentPlatformMap()

    private val platform = currentPlatform()

    private val toSave: (String, Path) -> Unit =
        if (platform.isMacos()) {
            ::macSaveExtIcon
        } else if (platform.isWindows()) {
            ::windowsSaveExtIcon
        } else if (platform.isLinux()) {
            ::linuxSaveExtIcon
        } else {
            throw IllegalStateException("Unsupported platform: $platform")
        }

    override fun resolve(key: String): Path {
        return pathProvider.resolve("$key.png", AppFileType.FILE_EXT_ICON)
    }

    override fun exist(result: Path): Boolean {
        return result.toFile().exists()
    }

    override fun loggerWarning(
        value: String,
        e: Exception,
    ) {
        logger.warn { "Failed to load icon for file extension: $value" }
    }

    override fun save(
        key: String,
        result: Path,
    ) {
        toSave(key, result)
    }

    override fun convertToKey(value: String): String {
        return value
    }
}

private fun macSaveExtIcon(
    key: String,
    path: Path,
) {
    MacosApi.INSTANCE.saveIconByExt(key, path.toString())
}

private fun windowsSaveExtIcon(
    key: String,
    path: Path,
) {
    User32.getAndSaveFileExtensionIcon(key, path.toString())
}

private fun linuxSaveExtIcon(
    key: String,
    path: Path,
) {
    // todo
}
