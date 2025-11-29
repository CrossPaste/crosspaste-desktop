package com.crosspaste.platform.macos

import com.crosspaste.platform.macos.api.MacosApi
import com.crosspaste.platform.macos.api.MacosApi.Companion.INSTANCE
import com.sun.jna.Pointer

object MacAppUtils {

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

    fun setWindowLevelScreenSaver(windowPtr: Pointer?) {
        INSTANCE.setWindowLevelScreenSaver(windowPtr)
    }

    fun bringToFront(appName: String) {
        INSTANCE.bringToFront(appName)
    }

    fun getCurrentActiveAppInfo(): String? = MacosApi.getString(INSTANCE.getCurrentActiveAppInfo())

    fun checkAccessibilityPermissions(): Boolean = INSTANCE.checkAccessibilityPermissions()

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
    ): Boolean = INSTANCE.createThumbnail(originalImagePath, thumbnailImagePath, metadataPath)
}
