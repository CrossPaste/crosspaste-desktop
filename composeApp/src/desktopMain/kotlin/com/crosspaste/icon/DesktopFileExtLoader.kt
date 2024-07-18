package com.crosspaste.icon

import com.crosspaste.app.AppFileType
import com.crosspaste.image.ImageService
import com.crosspaste.os.linux.FreedesktopUtils.saveExtIcon
import com.crosspaste.os.macos.api.MacosApi
import com.crosspaste.os.windows.JIconExtract
import com.crosspaste.path.DesktopPathProvider
import com.crosspaste.path.PathProvider
import com.crosspaste.platform.currentPlatform
import com.crosspaste.utils.ConcurrentPlatformMap
import com.crosspaste.utils.PlatformLock
import com.crosspaste.utils.createConcurrentPlatformMap
import com.crosspaste.utils.extension
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path

object DesktopFileExtLoader : ConcurrentLoader<Path, Path>, FileExtIconLoader {

    private val logger = KotlinLogging.logger {}

    private val pathProvider: PathProvider = DesktopPathProvider

    override val lockMap: ConcurrentPlatformMap<Path, PlatformLock> = createConcurrentPlatformMap()

    private val platform = currentPlatform()

    private val toSave: (String, Path, Path) -> Unit =
        if (platform.isMacos()) {
            { key, _, result -> macSaveExtIcon(key, result) }
        } else if (platform.isWindows()) {
            { _, value, result -> windowsSaveExtIcon(value, result) }
        } else if (platform.isLinux()) {
            { key, _, result -> linuxSaveExtIcon(key, result) }
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
        value: Path,
        e: Exception,
    ) {
        logger.warn { "Failed to load icon for file extension: $value" }
    }

    override fun save(
        key: String,
        value: Path,
        result: Path,
    ) {
        toSave(key, value, result)
    }

    override fun convertToKey(value: Path): String {
        return value.extension
    }
}

private fun macSaveExtIcon(
    key: String,
    savePath: Path,
) {
    MacosApi.INSTANCE.saveIconByExt(key, savePath.toString())
}

private fun windowsSaveExtIcon(
    filePath: Path,
    savePath: Path,
) {
    JIconExtract.getIconForFile(512, 512, filePath.toFile())?.let { icon ->
        ImageService.writeImage(icon, "png", savePath.toNioPath())
    }
}

private fun linuxSaveExtIcon(
    key: String,
    savePath: Path,
) {
    saveExtIcon(key, savePath)
}
