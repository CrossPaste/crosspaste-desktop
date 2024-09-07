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

    fun searchToBack(
        appName: String,
        toPaste: Boolean,
        array: Pointer,
        count: Int,
    ) {
        INSTANCE.searchToBack(appName, toPaste, array, count)
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
        try {
            block(this)
        } finally {
            this.forEach { it.close() }
        }
    }
}
