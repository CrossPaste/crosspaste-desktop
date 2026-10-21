package com.crosspaste.image

import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppName
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Stores CrossPaste's own Mac-style icon (transparent padding included) as the
 * source icon of the pastes it creates itself, such as OCR and plugin output,
 * so they load, crop and tint exactly like any captured app icon. Rewritten on
 * every launch so the icon follows the bundled logo across upgrades.
 */
suspend fun installCrossPasteSourceIcon(
    appInfo: AppInfo,
    userDataPathProvider: UserDataPathProvider,
    fileUtils: FileUtils = getFileUtils(),
) {
    runCatching {
        val bytes = Res.readBytes("drawable/crosspaste_mac.png")
        val iconPath = userDataPathProvider.resolveIconPath(appInfo.appInstanceId, AppName)
        if (fileUtils.existFile(iconPath) && fileUtils.getFileSize(iconPath) == bytes.size.toLong()) {
            return
        }
        val tempPath = iconPath.parent?.resolve("${iconPath.name}.tmp") ?: return
        fileUtils.fileSystem.write(tempPath) { write(bytes) }
        fileUtils.moveFile(tempPath, iconPath).getOrThrow()
    }.onFailure { logger.warn(it) { "Failed to install the CrossPaste source icon" } }
}
