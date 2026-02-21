package com.crosspaste.platform.macos.api

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference

interface MacosApi : Library {

    fun getPasteboardChangeCount(
        currentChangeCount: Int,
        remote: IntByReference,
        isCrossPaste: IntByReference,
    ): Int

    fun getPassword(
        service: String,
        account: String,
    ): Pointer?

    fun setPassword(
        service: String,
        account: String,
        password: String,
    ): Boolean

    fun updatePassword(
        service: String,
        account: String,
        password: String,
    ): Boolean

    fun deletePassword(
        service: String,
        account: String,
    ): Boolean

    fun getComputerName(): Pointer?

    fun getHardwareUUID(): Pointer?

    fun getCurrentActiveAppInfo(): Pointer?

    fun getRunningApplications(): Pointer?

    fun saveAppIcon(
        bundleIdentifier: String,
        path: String,
    )

    fun mainToBack(appName: String)

    fun mainToBackAndPaste(
        appName: String,
        array: Pointer,
        count: Int,
    )

    fun searchToBack(appName: String)

    fun searchToBackAndPaste(
        appName: String,
        array: Pointer,
        count: Int,
    )

    fun setWindowLevelScreenSaver(windowPtr: Pointer?)

    fun applyAcrylicBackground(
        windowPtr: Pointer?,
        isDark: Boolean,
    )

    fun bringToFront(windowTitle: String)

    fun simulatePasteCommand(
        array: Pointer,
        count: Int,
    )

    fun checkAccessibilityPermissions(): Boolean

    fun saveIconByExt(
        ext: String,
        path: String,
    )

    fun createThumbnail(
        originalImagePath: String,
        thumbnailImagePath: String,
        metadataPath: String,
    ): Boolean

    fun trayInit(
        iconData: ByteArray,
        iconDataLength: Int,
        tooltip: String,
        leftClickCallback: LeftClickCallback,
    ): Boolean

    fun trayAddMenuItem(
        itemId: Int,
        title: String,
        enabled: Boolean,
    )

    fun trayAddSeparator()

    fun traySetCallback(callback: MenuCallback)

    fun trayUpdateMenuItem(
        itemId: Int,
        title: String,
        enabled: Boolean,
    )

    fun trayCleanup()

    companion object {
        val INSTANCE: MacosApi = Native.load("MacosApi", MacosApi::class.java)

        fun getString(ptr: Pointer?): String? {
            val pointer = ptr ?: return null
            return try {
                pointer.getString(0)
            } finally {
                Native.free(Pointer.nativeValue(pointer))
            }
        }
    }
}

fun interface MenuCallback : Callback {
    fun invoke(itemId: Int)
}

fun interface LeftClickCallback : Callback {
    fun invoke()
}
