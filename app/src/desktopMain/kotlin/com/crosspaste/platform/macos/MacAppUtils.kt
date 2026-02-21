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

    fun applyAcrylicBackground(
        windowPtr: Pointer?,
        isDark: Boolean,
    ) {
        INSTANCE.applyAcrylicBackground(windowPtr, isDark)
    }

    fun bringToFront(appName: String) {
        INSTANCE.bringToFront(appName)
    }

    fun getCurrentActiveAppInfo(): String? = MacosApi.getString(INSTANCE.getCurrentActiveAppInfo())

    fun getRunningApplications(): List<Pair<String, String>> {
        val raw = MacosApi.getString(INSTANCE.getRunningApplications()) ?: return emptyList()
        return raw.split("\n\n").mapNotNull { entry ->
            val parts = entry.split("\n", limit = 2)
            if (parts.size == 2) Pair(parts[0], parts[1]) else null
        }
    }

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
