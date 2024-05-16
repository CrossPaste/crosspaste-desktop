package com.clipevery.os.linux.api

import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.unix.X11
import com.sun.jna.platform.unix.X11.CurrentTime
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
            INSTANCE.XOpenDisplay(null)?.let {
                WMCtrl.get_active_window_class(it)?.let { pair ->
                    pair.second?.let { className ->
                        println("className = $className")
                    }
                }
            }
        }

        fun getAppUniqueIdentifier(
            display: X11.Display,
            window: X11.Window,
        ): String? {
            val pid = get_window_pid(display, window)
            val cmdPath = getExecutablePathFromPid(pid)

            return cmdPath
        }

        fun getExecutablePathFromPid(pid: Int): String? {
            val procPath = "/proc/$pid/exe"
            return File(procPath).canonicalPath
        }

        fun get_property(
            disp: X11.Display?,
            win: X11.Window?,
            xa_prop_type: X11.Atom,
            prop_name: String?,
            size: NativeLongByReference?,
        ): Pointer? {
            val xa_ret_type = X11.AtomByReference()
            val ret_format = IntByReference()
            val ret_nitems = NativeLongByReference()
            val ret_bytes_after = NativeLongByReference()
            val ret_prop = PointerByReference()

            val xa_prop_name: X11.Atom = INSTANCE.XInternAtom(disp, prop_name, false)

            /*
             * MAX_PROPERTY_VALUE_LEN / 4 explanation (XGetWindowProperty manpage):
             *
             * long_length = Specifies the length in 32-bit multiples of the data to
             * be retrieved.
             *
             * NOTE: see
             * http://mail.gnome.org/archives/wm-spec-list/2003-March/msg00067.html
             * In particular:
             *
             * When the X window system was ported to 64-bit architectures, a rather
             * peculiar design decision was made, 32-bit quantities such as Window
             * IDs, atoms, etc, were kept as longs in the client side APIs, even
             * when long was changed to 64 bit.
             */
            if (INSTANCE.XGetWindowProperty(
                    disp, win, xa_prop_name, NativeLong(0),
                    NativeLong(4096 / 4), false,
                    xa_prop_type, xa_ret_type, ret_format, ret_nitems,
                    ret_bytes_after, ret_prop,
                ) != X11.Success
            ) {
                logger.info { "Cannot get $prop_name property." }
                return null
            }

            if ((xa_ret_type.value == null) ||
                (
                    xa_ret_type.value.toLong() !=
                        xa_prop_type
                            .toLong()
                )
            ) {
                logger.info { "Invalid type of $prop_name property." }
                g_free(ret_prop.pointer)
                return null
            }

            if (size != null) {
                var tmp_size = (
                    (ret_format.value / 8) *
                        ret_nitems.value.toLong()
                )
                // Correct 64 Architecture implementation of 32 bit data
                if (ret_format.value == 32) {
                    tmp_size *= (NativeLong.SIZE / 4).toLong()
                }
                size.value = NativeLong(tmp_size)
            }

            return ret_prop.value
        }

        fun get_window_pid(
            disp: X11.Display,
            win: X11.Window,
        ): Int {
            val pid =
                get_property_as_int(
                    disp,
                    win,
                    X11.XA_CARDINAL,
                    "_NET_WM_PID",
                )
            return if ((pid == null)) -1 else pid
        }

        private fun get_property_as_int(
            disp: X11.Display,
            win: X11.Window,
            xa_prop_type: X11.Atom,
            prop_name: String,
        ): Int? {
            var intProp: Int? = null

            val prop =
                get_property(
                    disp,
                    win,
                    xa_prop_type,
                    prop_name,
                    null,
                )
            if (prop != null) {
                intProp = prop.getInt(0)
                g_free(prop)
            }

            return intProp
        }

        private fun g_free(pointer: Pointer?) {
            if (pointer != null) {
                INSTANCE.XFree(pointer)
            }
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
