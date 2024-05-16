package com.clipevery.app

import com.clipevery.os.linux.api.X11Api
import io.github.oshai.kotlinlogging.KotlinLogging

class LinuxWindowManager : WindowManager {

    private val logger = KotlinLogging.logger {}

//    private var prevLinuxAppInfo: LinuxAppInfo? = null

    override fun getPrevAppName(): String? {
        return null
    }

    override fun getCurrentActiveAppName(): String? {
        return null
    }

    override suspend fun bringToFront(windowTitle: String) {
        X11Api.bringToFront(windowTitle)
    }

    override suspend fun bringToBack(
        windowTitle: String,
        toPaste: Boolean,
    ) {
        X11Api.bringToBack(windowTitle, "", toPaste)
    }
}
