package com.crosspaste.platform.macos.api

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.IntByReference
import java.awt.GraphicsDevice

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

    fun getCurrentActiveApp(): Pointer?

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

    fun bringToFront(windowTitle: String): Pointer

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

        fun getString(ptr: Pointer?): String? {
            val pointer = ptr ?: return null
            try {
                return pointer.getString(0)
            } finally {
                Native.free(Pointer.nativeValue(pointer))
            }
        }
    }
}

@Structure.FieldOrder("x", "y", "width", "height", "displayID")
class WindowInfo : Structure, AutoCloseable {
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

    fun contained(graphicsDevice: GraphicsDevice): Boolean {
        val displayWidth = graphicsDevice.displayMode.width
        val displayHeight = graphicsDevice.displayMode.height
        val bound = graphicsDevice.defaultConfiguration.bounds
        return bound.x <= this.x &&
            bound.x + displayWidth >= this.x + this.width &&
            bound.y <= this.y &&
            bound.y + displayHeight >= this.y + this.height
    }

    override fun toString(): String {
        return "WindowInfo(x=$x, y=$y, width=$width, height=$height, displayID=$displayID)"
    }

    override fun close() {
        clear()
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