package com.crosspaste.os.macos.api

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
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

    fun getTrayWindowInfos(pid: Long): Pointer?

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

    fun saveIconByExt(
        ext: String,
        path: String,
    )

    companion object {
        val INSTANCE: MacosApi = Native.load("MacosApi", MacosApi::class.java)

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
    }
}

@Structure.FieldOrder("x", "y", "width", "height", "displayID")
class WindowInfo : Structure {
    @JvmField var x: Float = 0f

    @JvmField var y: Float = 0f

    @JvmField var width: Float = 0f

    @JvmField var height: Float = 0f

    @JvmField var displayID: Int = 0

    constructor() : super()

    constructor(p: Pointer) : super(p) {
        read()
    }

    fun contains(
        x: Int,
        y: Int,
    ): Boolean {
        return x >= this.x && x <= this.x + width && y >= this.y && y <= this.y + height
    }

    override fun toString(): String {
        return "WindowInfo(x=$x, y=$y, width=$width, height=$height, displayID=$displayID)"
    }
}

@Structure.FieldOrder("count", "windowInfos")
class WindowInfoArray(p: Pointer) : Structure(p) {
    @JvmField var count: Int = 0

    @JvmField var windowInfos: Pointer? = null

    init {
        read()
    }
}
