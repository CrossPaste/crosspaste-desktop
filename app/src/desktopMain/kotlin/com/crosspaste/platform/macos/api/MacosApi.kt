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

    fun writeFilesToPasteboard(
        count: Int,
        resolver: FileResolverCallback,
    ): Int

    fun getProvideDataCallCount(): Int

    fun resetProvideDataCallCount()

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

    fun setWindowLevelPopUpMenu(windowPtr: Pointer?)

    fun applyAcrylicBackground(
        windowPtr: Pointer?,
        isDark: Boolean,
    )

    // showDockIcon is an Int (0/1) rather than Boolean: JNA marshals Java boolean
    // as a 32-bit -1 for true, which does not match Swift's zero-extended i1 Bool ABI
    fun bringToFront(
        windowTitle: String,
        showDockIcon: Int,
    )

    fun setDockIconVisibility(showDockIcon: Int)

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

    fun createVideoThumbnail(
        videoPath: String,
        thumbnailPath: String,
    ): Boolean

    fun startNetworkStateMonitor(callback: NetworkChangeCallback)

    fun stopNetworkStateMonitor()

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

fun interface FileResolverCallback : Callback {
    fun invoke(
        index: Int,
        buffer: Pointer,
        bufferSize: Int,
    ): Int
}

fun interface NetworkChangeCallback : Callback {
    fun invoke()
}
