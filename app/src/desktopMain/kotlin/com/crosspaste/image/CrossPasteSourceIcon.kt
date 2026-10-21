package com.crosspaste.image

import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppName
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.path.UserDataPathProvider
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
) {
    runCatching {
        val bytes = Res.readBytes("drawable/crosspaste_mac.png")
        val iconPath = userDataPathProvider.resolveIconPath(appInfo.appInstanceId, AppName)
        getFileUtils().fileSystem.write(iconPath) { write(bytes) }
    }.onFailure { logger.warn(it) { "Failed to install the CrossPaste source icon" } }
}
