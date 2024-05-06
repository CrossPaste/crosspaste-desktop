package com.clipevery.app

import com.clipevery.os.macos.api.MacosApi
import com.clipevery.path.DesktopPathProvider
import com.clipevery.path.PathProvider
import com.clipevery.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.io.path.absolutePathString

class MacWindowManager : WindowManager {

    private val logger = KotlinLogging.logger {}

    private val pathProvider: PathProvider = DesktopPathProvider

    private val ioScope = CoroutineScope(ioDispatcher + SupervisorJob())

    private val clipeveryBundleID = System.getProperty("mac.bundleID")

    private var prevAppName: String? = null

    override fun getCurrentActiveApp(): String? {
        MacosApi.INSTANCE.getCurrentActiveApp()?.let {
            val result = it.split(" ", limit = 2)
            if (result.size > 1) {
                val bundleIdentifier = result[0]
                val localizedName = result[1]
                ioScope.launch {
                    saveImagePathByApp(bundleIdentifier, localizedName)
                }
                return localizedName
            }
        }
        return null
    }

    @Synchronized
    private fun saveImagePathByApp(
        bundleIdentifier: String,
        localizedName: String,
    ) {
        val appImagePath = pathProvider.resolve("$localizedName.png", AppFileType.ICON)
        if (!appImagePath.toFile().exists()) {
            MacosApi.INSTANCE.saveAppIcon(bundleIdentifier, appImagePath.absolutePathString())
        }
    }

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
        MacosApi.INSTANCE.bringToBack(windowTitle, prevAppName ?: "", toPaste)
    }
}
