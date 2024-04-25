package com.clipevery.os.macos.api

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.ptr.IntByReference

interface MacosApi : Library {

    fun getClipboardChangeCount(
        currentChangeCount: Int,
        remote: IntByReference,
        isClipevery: IntByReference,
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

    fun bringToFront(windowTitle: String): String

    fun activeApp(appName: String)

    companion object {
        val INSTANCE: MacosApi = Native.load("MacosApi", MacosApi::class.java)
    }
}
