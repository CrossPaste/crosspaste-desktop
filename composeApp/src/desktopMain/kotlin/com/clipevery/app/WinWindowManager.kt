package com.clipevery.app

import com.clipevery.app.DesktopAppWindowManager.searchWindowTitle
import com.clipevery.os.windows.api.User32
import com.clipevery.path.DesktopPathProvider
import com.clipevery.path.PathProvider
import com.clipevery.utils.ioDispatcher
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.sun.jna.platform.win32.WinDef.HWND
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.io.path.absolutePathString

class WinWindowManager : WindowManager {

    private val logger = KotlinLogging.logger {}

    private val pathProvider: PathProvider = DesktopPathProvider

    private val ioScope = CoroutineScope(ioDispatcher + SupervisorJob())

    private var prevWinAppInfo: WinAppInfo? = null

    private val fileDescriptorCache: LoadingCache<String, String> =
        CacheBuilder.newBuilder()
            .maximumSize(20)
            .build(
                object : CacheLoader<String, String>() {
                    override fun load(filePath: String): String {
                        User32.getFileDescription(filePath)?.let {
                            ioScope.launch {
                                saveAppImage(filePath, it)
                            }
                            return it
                        }
                        return ""
                    }
                },
            )

    override fun getPrevAppName(): String? {
        prevWinAppInfo?.filePath?.let {
            return fileDescriptorCache[it].ifEmpty {
                null
            }
        } ?: run {
            return null
        }
    }

    override fun getCurrentActiveAppName(): String? {
        User32.getActiveWindowProcessFilePath()?.let {
            return fileDescriptorCache[it].ifEmpty {
                null
            }
        } ?: run {
            return null
        }
    }

    @Synchronized
    private fun saveAppImage(
        exeFilePath: String,
        fileDescription: String,
    ) {
        val iconPath = pathProvider.resolve("$fileDescription.png", AppFileType.ICON)
        if (!iconPath.toFile().exists()) {
            User32.extractAndSaveIcon(exeFilePath, iconPath.absolutePathString())
        }
    }

    override suspend fun bringToFront(windowTitle: String) {
        logger.info { "$windowTitle bringToFront Clipevery" }
        if (windowTitle == searchWindowTitle) {
            // to wait for the search window to be ready
            delay(500)
        }
        prevWinAppInfo = User32.bringToFront(windowTitle)
    }

    override suspend fun bringToBack(
        windowTitle: String,
        toPaste: Boolean,
    ) {
        logger.info { "$windowTitle bringToBack Clipevery" }
        User32.bringToBack(windowTitle, prevWinAppInfo?.hwnd, toPaste)
    }
}

data class WinAppInfo(val hwnd: HWND, val filePath: String) {

    override fun toString(): String {
        return "WinAppInfo(hwnd=$hwnd, filePath='$filePath')"
    }
}
