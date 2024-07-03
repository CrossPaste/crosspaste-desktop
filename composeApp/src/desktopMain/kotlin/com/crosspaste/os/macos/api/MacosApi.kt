package com.crosspaste.os.macos.api

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference

interface MacosApi : Library {

    fun getClipboardChangeCount(
        currentChangeCount: Int,
        remote: IntByReference,
        isCrossPaste: IntByReference,
    ): Int

    fun getPassword(
        service: String,
        account: String,
    ): String?

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

    fun getComputerName(): String?

    fun getHardwareUUID(): String?

    fun getCurrentActiveApp(): String?

    fun saveAppIcon(
        bundleIdentifier: String,
        path: String,
    )

    fun mainToBack(appName: String)

    fun searchToBack(
        appName: String,
        toPaste: Boolean,
        array: Pointer,
        count: Int,
    )

    fun bringToFront(windowTitle: String): String

    fun simulatePasteCommand(
        array: Pointer,
        count: Int,
    )

    fun checkAccessibilityPermissions(): Boolean

    companion object {
        val INSTANCE: MacosApi = Native.load("MacosApi", MacosApi::class.java)
    }
}
