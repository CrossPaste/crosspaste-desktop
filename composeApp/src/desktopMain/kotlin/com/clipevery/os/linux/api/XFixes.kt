package com.clipevery.os.linux.api

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.platform.unix.X11
import com.sun.jna.platform.unix.X11.Atom

interface XFixes : Library {

    fun XFixesSelectSelectionInput(
        display: X11.Display,
        rootWindow: X11.Window,
        selectionBuffer: Atom,
        XfixesSelector: NativeLong,
    ): X11.Window

    companion object {

        val INSTANCE: XFixes = Native.load("Xfixes", XFixes::class.java)
    }
}
