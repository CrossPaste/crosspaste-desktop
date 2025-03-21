package com.crosspaste.platform.macos

import com.crosspaste.platform.macos.api.MacosApi
import com.crosspaste.platform.macos.api.MacosApi.Companion.INSTANCE
import com.crosspaste.platform.macos.api.WindowInfo
import com.crosspaste.platform.macos.api.WindowInfoArray
import com.sun.jna.Pointer

object MacAppUtils {

    fun getCurrentActiveApp(): String? {
        return MacosApi.getString(INSTANCE.getCurrentActiveApp())
    }

    fun saveAppIcon(
        bundleIdentifier: String,
        path: String,
    ) {
        INSTANCE.saveAppIcon(bundleIdentifier, path)
    }

    fun mainToBack(appName: String) {
        INSTANCE.mainToBack(appName)
    }

    fun mainToBackAndPaste(
        appName: String,
        array: Pointer,
        count: Int,
    ) {
        INSTANCE.mainToBackAndPaste(appName, array, count)
    }

    fun searchToBack(appName: String) {
        INSTANCE.searchToBack(appName)
    }

    fun searchToBackAndPaste(
        appName: String,
        array: Pointer,
        count: Int,
    ) {
        INSTANCE.searchToBackAndPaste(appName, array, count)
    }

    fun bringToFront(appName: String): String {
        return MacosApi.getString(INSTANCE.bringToFront(appName))!!
    }

    fun checkAccessibilityPermissions(): Boolean {
        return INSTANCE.checkAccessibilityPermissions()
    }

    fun saveIconByExt(
        ext: String,
        path: String,
    ) {
        INSTANCE.saveIconByExt(ext, path)
    }

    fun createThumbnail(
        originalImagePath: String,
        thumbnailImagePath: String,
        metadataPath: String,
    ): Boolean {
        return INSTANCE.createThumbnail(originalImagePath, thumbnailImagePath, metadataPath)
    }

    fun getTrayWindowInfos(pid: Long): List<WindowInfo> {
        return INSTANCE.getTrayWindowInfos(pid)?.let {
            val windowInfoArray = WindowInfoArray(it)
            windowInfoArray.read()

            val count = windowInfoArray.count
            val windowInfos = windowInfoArray.windowInfos!!

            (0 until count).map { i ->
                val windowInfo = WindowInfo(windowInfos.share((i * WindowInfo().size()).toLong()))
                windowInfo.read()
                windowInfo
            }
        } ?: emptyList()
    }

    fun List<WindowInfo>.useAll(block: (List<WindowInfo>) -> Unit) {
        runCatching {
            block(this)
        }.let {
            this.forEach { it.close() }
        }
    }
}
