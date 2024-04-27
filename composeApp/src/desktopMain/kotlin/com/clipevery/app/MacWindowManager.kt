package com.clipevery.app

import com.clipevery.app.DesktopAppWindowManager.mainWindowTitle
import com.clipevery.os.macos.api.MacosApi
import io.github.oshai.kotlinlogging.KotlinLogging

class MacWindowManager : WindowManager {

    private val logger = KotlinLogging.logger {}

    private val clipeveryBundleID = System.getProperty("mac.bundleID")

    private var prevAppName: String? = null

    override suspend fun bringToFront(windowTitle: String) {
        logger.info { "$windowTitle bringToFront Clipevery" }
        MacosApi.INSTANCE.bringToFront(windowTitle).let {
            if (it != clipeveryBundleID) {
                prevAppName = it
                logger.info { "save prevAppName $it" }
            }
        }
    }

    override suspend fun bringToBack(
        windowTitle: String,
        toPaste: Boolean,
    ) {
        logger.info { "$windowTitle bringToBack Clipevery" }
        MacosApi.INSTANCE.bringToBack(mainWindowTitle, prevAppName ?: "", toPaste)
    }
}
