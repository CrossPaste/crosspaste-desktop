package com.clipevery.os.linux.api

import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.unix.X11
import com.sun.jna.platform.unix.X11.CurrentTime
import com.sun.jna.platform.unix.X11.RevertToParent
import com.sun.jna.platform.unix.X11.RevertToPointerRoot
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.NativeLongByReference
import com.sun.jna.ptr.PointerByReference
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

interface X11Api : X11 {

    fun XSetInputFocus(
        display: X11.Display,
        w: X11.Window,
        revert_to: Int,
        time: Int,
    ): Int

    fun XGetInputFocus(
        display: X11.Display,
        focus_return: X11.WindowByReference,
        revert_to_return: IntByReference,
    ): Int

    fun XGetTextProperty(
        display: X11.Display,
        window: X11.Window,
        textProp: XTextProperty.ByReference,
        property: X11.Atom,
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

        private val logger = KotlinLogging.logger {}

        val INSTANCE: X11Api = Native.load("X11", X11Api::class.java)

        fun bringToFront(windowTitle: String) {
            val x11 = INSTANCE
            val display = x11.XOpenDisplay(null) ?: throw RuntimeException("Unable to open X Display")
            val rootWindow = x11.XDefaultRootWindow(display)

            val windowByReference = X11.WindowByReference()

            val intByReference = IntByReference()

            x11.XGetInputFocus(display, windowByReference, intByReference)

            val focusedWindow = windowByReference.value

            if (focusedWindow != X11.Window.None) {
                val atom = x11.XInternAtom(display, "_NET_WM_PID", false)
                getWindowPid(display, focusedWindow, atom)?.let { pid ->
                    logger.info { "Focused window: $pid" }
                }
            }

            val window = findWindowByTitle(display, rootWindow, windowTitle)
            if (window != null) {
                x11.XMapWindow(display, window)
                x11.XRaiseWindow(display, window)

                // Attempt to set the window as the active window and bring it to the front
                x11.XSetInputFocus(display, window, RevertToParent, CurrentTime)
            } else {
                logger.error { "Window with title '$windowTitle' not found." }
            }

            x11.XCloseDisplay(display)
        }

        fun getAppUniqueIdentifier(
            display: X11.Display,
            window: X11.Window,
        ): String? {
            val x11 = X11.INSTANCE
            val pidAtom = x11.XInternAtom(display, "_NET_WM_PID", true)
            if (pidAtom == X11.Atom.None) return null

            val pid = getWindowPid(display, window, pidAtom) ?: return null
            val cmdPath = getExecutablePathFromPid(pid)

            return cmdPath
        }

        fun getWindowPid(
            display: X11.Display,
            window: X11.Window,
            pidAtom: X11.Atom,
        ): Int? {
            val actual_type_return = X11.AtomByReference()
            val actual_format_return = IntByReference()
            val nitems_return = NativeLongByReference()
            val bytes_after_return = NativeLongByReference()
            val prop_return = PointerByReference()

            val status: Int =
                INSTANCE.XGetWindowProperty(
                    display, window, pidAtom,
                    NativeLong(0), // long_offset
                    NativeLong(1), // long_length
                    false, // delete
                    X11.Atom(0),
                    actual_type_return,
                    actual_format_return,
                    nitems_return,
                    bytes_after_return,
                    prop_return,
                )

            if (status == X11.Success && nitems_return.value.toLong() > 0) {
                val pid = prop_return.value.getInt(0)
                return pid
            }
            return null
        }

        fun getExecutablePathFromPid(pid: Int): String? {
            val procPath = "/proc/$pid/exe"
            return File(procPath).canonicalPath
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

@Structure.FieldOrder("value", "encoding", "format", "nitems")
open class XTextProperty : Structure() {
    @JvmField var value: PointerByReference = PointerByReference()

    @JvmField var encoding: X11.Atom = X11.Atom()

    @JvmField var format: Int = 0

    @JvmField var nitems: NativeLong = NativeLong(0)

    class ByReference : XTextProperty(), Structure.ByReference
}
