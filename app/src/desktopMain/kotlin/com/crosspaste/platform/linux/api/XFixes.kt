package com.crosspaste.platform.linux.api

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.unix.X11
import com.sun.jna.platform.unix.X11.Atom
import com.sun.jna.ptr.IntByReference

interface XFixes : Library {

    fun XFixesSelectSelectionInput(
        display: X11.Display,
        rootWindow: X11.Window,
        selectionBuffer: Atom,
        XfixesSelector: NativeLong,
    ): X11.Window

    fun XFixesQueryExtension(
        display: X11.Display,
        event_base_return: IntByReference,
        error_base_return: IntByReference,
    ): Int

    fun XFixesGetCursorImage(dpy: X11.Display?): XFixesCursorImage?

    companion object {

        const val XFixesSelectionNotify = 0

        val INSTANCE: XFixes = Native.load("Xfixes", XFixes::class.java)
    }
}

@Structure.FieldOrder(
    "type",
    "serial",
    "send_event",
    "display",
    "window",
    "subtype",
    "owner",
    "selection",
    "timestamp",
    "selectionTimestamp",
)
class XFixesSelectionNotifyEvent(ptr: Pointer) : Structure(ptr) {
    @JvmField var type: Int = 0

    @JvmField var serial: NativeLong? = null

    @JvmField var send_event: Int = 0

    @JvmField var display: X11.Display? = null

    @JvmField var window: X11.Window? = null

    @JvmField var subtype: Int = 0

    @JvmField var owner: X11.Window? = null

    @JvmField var selection: Atom? = null

    @JvmField var timestamp: NativeLong? = null

    @JvmField var selectionTimestamp: NativeLong? = null

    init {
        read()
    }
}

@Structure.FieldOrder(
    "x",
    "y",
    "width",
    "height",
    "xhot",
    "yhot",
    "cursor_serial",
    "timestamp",
    "pixels",
)
class XFixesCursorImage(ptr: Pointer) : Structure(ptr) {
    @JvmField var x: Int = 0

    @JvmField var y: Int = 0

    @JvmField var width: Int = 0

    @JvmField var height: Int = 0

    @JvmField var xhot: Int = 0

    @JvmField var yhot: Int = 0

    @JvmField var cursor_serial: Int = 0

    @JvmField var timestamp: NativeLong = NativeLong(0)

    @JvmField var pixels: Pointer? = null

    init {
        read()
    }
}
