package com.crosspaste.image

import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.getPlatform
import com.crosspaste.platform.linux.FreedesktopUtils.saveExtIcon
import com.crosspaste.platform.macos.MacAppUtils
import com.crosspaste.platform.windows.JIconExtract
import okio.Path
import java.awt.image.BufferedImage

class DesktopFileExtLoader(
    imageWriter: ImageWriter<BufferedImage>,
    userDataPathProvider: UserDataPathProvider,
) : AbstractFileExtImageLoader(userDataPathProvider) {

    private val platform = getPlatform()

    private val toSave: (String, Path, Path) -> Unit =
        if (platform.isMacos()) {
            { key, _, result -> macSaveExtIcon(key, result) }
        } else if (platform.isWindows()) {
            { _, value, result -> windowsSaveExtIcon(value, result, imageWriter) }
        } else if (platform.isLinux()) {
            { key, _, result -> linuxSaveExtIcon(key, result) }
        } else {
            throw IllegalStateException("Unsupported platform: $platform")
        }

    override fun save(
        key: String,
        value: Path,
        result: Path,
    ) {
        toSave(key, value, result)
    }
}

private fun macSaveExtIcon(
    key: String,
    savePath: Path,
) {
    MacAppUtils.saveIconByExt(key, savePath.toString())
}

private fun windowsSaveExtIcon(
    filePath: Path,
    savePath: Path,
    imageWriter: ImageWriter<BufferedImage>,
) {
    JIconExtract.getIconForFile(512, 512, filePath.toFile())?.let { icon ->
        imageWriter.writeImage(icon, "png", savePath)
    }
}

private fun linuxSaveExtIcon(
    key: String,
    savePath: Path,
) {
    saveExtIcon(key, savePath)
}
