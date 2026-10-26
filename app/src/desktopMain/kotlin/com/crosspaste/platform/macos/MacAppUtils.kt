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

    /** "bundleIdentifier\nlocalizedName" for the app bundle at [path], or null if it is not one. */
    fun getAppInfoAtPath(path: String): String? = MacosApi.getString(INSTANCE.getAppInfoAtPath(path))

    fun saveAppIconAtPath(
        appPath: String,
        iconPath: String,
    ): Boolean = INSTANCE.saveAppIconAtPath(appPath, iconPath)

    fun mainToBack(appName: String) {
        INSTANCE.mainToBack(appName)
    }

    fun searchToBack(appName: String) {
        INSTANCE.searchToBack(appName)
    }

    fun setWindowLevelPopUpMenu(
        windowPtr: Pointer?,
        levelOffset: Int = 0,
    ) {
        INSTANCE.setWindowLevelPopUpMenu(windowPtr, levelOffset)
    }

    fun makeWindowNonActivating(windowPtr: Pointer?) {
        INSTANCE.makeWindowNonActivating(windowPtr)
    }

    fun applyAcrylicBackground(
        windowPtr: Pointer?,
        isDark: Boolean,
    ) {
        INSTANCE.applyAcrylicBackground(windowPtr, isDark)
    }

    fun bringToFront(
        appName: String,
        showDockIcon: Boolean,
    ) {
        INSTANCE.bringToFront(appName, if (showDockIcon) 1 else 0)
    }

    fun setDockIconVisibility(
        showDockIcon: Boolean,
        restoreActivation: Boolean,
    ) {
        INSTANCE.setDockIconVisibility(if (showDockIcon) 1 else 0, if (restoreActivation) 1 else 0)
    }

    /** Retitles the "About <app>" item in the macOS application menu. */
    fun setAboutMenuItemTitle(title: String) {
        INSTANCE.setAboutMenuItemTitle(title)
    }

    fun getCurrentActiveAppInfo(): String? = MacosApi.getString(INSTANCE.getCurrentActiveAppInfo())

    fun isApplicationActive(): Boolean = INSTANCE.isApplicationActive() != 0

    fun isPointerOnStatusItem(): Boolean = INSTANCE.isPointerOnStatusItem() != 0

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

    fun createVideoThumbnail(
        videoPath: String,
        thumbnailPath: String,
    ): Boolean = INSTANCE.createVideoThumbnail(videoPath, thumbnailPath)
}
