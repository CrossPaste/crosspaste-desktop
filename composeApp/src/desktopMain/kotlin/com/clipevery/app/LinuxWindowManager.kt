package com.clipevery.app

import com.clipevery.os.linux.api.X11Api
import com.clipevery.path.DesktopPathProvider
import com.clipevery.path.PathProvider
import com.clipevery.utils.ioDispatcher
import com.sun.jna.platform.unix.X11.Window
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LinuxWindowManager : WindowManager {

    private val logger = KotlinLogging.logger {}

    private val pathProvider: PathProvider = DesktopPathProvider

    private val ioScope = CoroutineScope(ioDispatcher + SupervisorJob())

    private var prevLinuxAppInfo: LinuxAppInfo? = null

    private val classNameSet: MutableSet<String> = mutableSetOf()

    override fun getPrevAppName(): String? {
        return prevLinuxAppInfo?.let {
            getAppName(it)
        }
    }

    override fun getCurrentActiveAppName(): String? {
        return X11Api.getActiveWindow()?.let { linuxAppInfo ->
            getAppName(linuxAppInfo)
        }
    }

    private fun getAppName(linuxAppInfo: LinuxAppInfo): String {
        val className = linuxAppInfo.className
        if (!classNameSet.contains(className)) {
            ioScope.launch {
                saveAppImage(linuxAppInfo.window, className)
            }
            classNameSet.add(className)
        }
        return className
    }

    @Synchronized
    private fun saveAppImage(
        window: Window,
        className: String,
    ) {
        val iconPath = pathProvider.resolve("$className.png", AppFileType.ICON)
        if (!iconPath.toFile().exists()) {
            X11Api.saveAppIcon(window, iconPath)
        }
    }

    override suspend fun bringToFront(windowTitle: String) {
        logger.info { "$windowTitle bringToFront Clipevery" }

        prevLinuxAppInfo = X11Api.bringToFront(windowTitle)
    }

    override suspend fun bringToBack(
        windowTitle: String,
        toPaste: Boolean,
    ) {
        logger.info { "$windowTitle bringToBack Clipevery" }
        X11Api.bringToBack(prevLinuxAppInfo, toPaste)
    }
}

data class LinuxAppInfo(val window: Window, val className: String) {

    override fun toString(): String {
        return "LinuxAppInfo(window=$window, className='$className')"
    }
}
