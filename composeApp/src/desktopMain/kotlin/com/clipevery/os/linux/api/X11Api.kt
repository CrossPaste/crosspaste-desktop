package com.clipevery.os.linux.api

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.unix.X11
import com.sun.jna.platform.unix.X11.CurrentTime
import com.sun.jna.platform.unix.X11.RevertToParent
import com.sun.jna.platform.unix.X11.RevertToPointerRoot
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference

interface X11Api : X11 {

    fun XSetInputFocus(
        display: X11.Display,
        w: X11.Window,
        revert_to: Int,
        time: Int,
    ): Int

    fun XRaiseWindow(
        display: X11.Display,
        w: X11.Window,
    )

    fun XLowerWindow(
        display: X11.Display,
        w: X11.Window,
    )

    companion object {

        val INSTANCE: X11Api = Native.load("X11", X11Api::class.java)

        fun bringToFront(windowTitle: String) {
            val x11 = INSTANCE
            val display = x11.XOpenDisplay(null) ?: throw RuntimeException("Unable to open X Display")
            val rootWindow = x11.XDefaultRootWindow(display)

            val window = findWindowByTitle(display, rootWindow, windowTitle)
            if (window != null) {
                // Attempt to set the window as the active window and bring it to the front
                val focusResult = x11.XSetInputFocus(display, window, RevertToParent, CurrentTime)
                if (focusResult == 0) {
                    x11.XRaiseWindow(display, window)
                    println("Window '$windowTitle' activated and brought to front.")
                } else {
                    println("Failed to set focus to window '$windowTitle'. Error code: $focusResult")
                }
            } else {
                println("Window with title '$windowTitle' not found.")
            }

            x11.XCloseDisplay(display)
        }

        fun bringToBack(
            windowTitle: String,
            appName: String,
            toPaste: Boolean,
        ) {
            val x11 = INSTANCE
            val display = x11.XOpenDisplay(null) ?: throw RuntimeException("Unable to open X Display")
            val rootWindow = x11.XDefaultRootWindow(display)

            val window = findWindowByTitle(display, rootWindow, windowTitle)
            if (window != null) {
                // Set the window to be in the background
                x11.XLowerWindow(display, window)

                // Attempt to set the input focus to the root window to effectively give up the focus
                val result = x11.XSetInputFocus(display, rootWindow, RevertToPointerRoot, CurrentTime)
                if (result == 0) {
                    println("Window '$windowTitle' sent to back and focus given up.")
                } else {
                    println("Failed to set focus to root window. Error code: $result")
                }
            } else {
                println("Window with title '$windowTitle' not found.")
            }

            x11.XCloseDisplay(display)
        }

        fun findWindowByTitle(
            display: X11.Display,
            window: X11.Window,
            title: String,
        ): X11.Window? {
            val x11 = INSTANCE

            val rootReturn = X11.WindowByReference()
            val parentReturn = X11.WindowByReference()
            val childrenReturn = PointerByReference()
            val nchildrenReturn = IntByReference()

            x11.XQueryTree(display, window, rootReturn, parentReturn, childrenReturn, nchildrenReturn)
            val nchildren = nchildrenReturn.value
            if (nchildren == 0) return null

            val children = childrenReturn.value.getPointerArray(0, nchildren)

            for (i in 0 until nchildren) {
                val child = X11.Window(Pointer.nativeValue(children[i]))
                val namePtr = PointerByReference()
                if (x11.XFetchName(display, child, namePtr) != 0) {
                    val name = namePtr.value.getString(0)
                    x11.XFree(namePtr.value)
                    if (title == name) {
                        return child
                    }
                }

                // Recursively search in child windows
                val found = findWindowByTitle(display, child, title)
                if (found != null) return found
            }

            return null
        }
    }
}
