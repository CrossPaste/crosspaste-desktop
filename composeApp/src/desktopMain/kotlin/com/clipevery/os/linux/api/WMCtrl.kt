package com.clipevery.os.linux.api

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.platform.unix.X11
import com.sun.jna.platform.unix.X11.Atom
import com.sun.jna.platform.unix.X11.AtomByReference
import com.sun.jna.platform.unix.X11.XClientMessageEvent
import com.sun.jna.platform.unix.X11.XEvent
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.NativeLongByReference
import com.sun.jna.ptr.PointerByReference
import dorkbox.jna.linux.Gtk.FALSE
import dorkbox.jna.linux.Gtk.TRUE
import io.github.oshai.kotlinlogging.KotlinLogging

object WMCtrl {

    private const val MAX_PROPERTY_VALUE_LEN: Long = 4096L

    private val logger = KotlinLogging.logger {}

    private val x11 = Native.load("X11", X11::class.java)

    const val EXIT_SUCCESS: Boolean = true
    const val EXIT_FAILURE: Boolean = false

    val options: Options = Options()

    private fun findWindow(
        x11: X11,
        display: X11.Display,
        root: X11.Window,
        depth: Int,
        findPid: Int,
    ): X11.Window? {
        val windowRef = X11.WindowByReference()
        val parentRef = X11.WindowByReference()
        val childrenRef = PointerByReference()
        val childCountRef = IntByReference()

        x11.XQueryTree(display, root, windowRef, parentRef, childrenRef, childCountRef)
        if (childrenRef.value == null) {
            return null
        }

        val ids: LongArray

        when (Native.LONG_SIZE) {
            java.lang.Long.BYTES -> {
                ids = childrenRef.value.getLongArray(0, childCountRef.value)
            }
            Integer.BYTES -> {
                val intIds = childrenRef.value.getIntArray(0, childCountRef.value)
                ids = LongArray(intIds.size)
                for (i in intIds.indices) {
                    ids[i] = intIds[i].toLong()
                }
            }
            else -> {
                throw IllegalStateException("Unexpected size for Native.LONG_SIZE" + Native.LONG_SIZE)
            }
        }

        for (id in ids) {
            if (id == 0L) {
                continue
            }
            val window: X11.Window = X11.Window(id)
            val name = X11.XTextProperty()
            x11.XGetWMName(display, window, name)
            if (name.value == null || name.value.trim { it <= ' ' } === "") continue

            val wPid: Int = WMCtrl.get_window_pid(display, window)
            if (wPid == findPid) {
                return window
            }

            x11.XFree(name.pointer)

            val rec: X11.Window? = findWindow(x11, display, window, depth + 1, findPid)
            if (rec != null) return rec
        }

        return null
    }

    fun switchDesktop(
        disp: X11.Display?,
        target: Long,
    ): Boolean {
        if (target < 0) {
            logger.error { "Invalid desktop ID.\n" }
            return false
        }
        if (client_msg(
                disp,
                x11.XDefaultRootWindow(disp),
                "_NET_CURRENT_DESKTOP",
                target,
                0,
                0,
                0,
                0,
            )
        ) {
            x11.XFlush(disp)
            return true
        }
        return false
    }

    fun getClientList(
        disp: X11.Display?,
        stackingOrder: Boolean,
    ): List<X11.Window>? {
        val size = NativeLongByReference()
        var clientList: Pointer? = null
        var msg: String? = null

        if (stackingOrder) {
            msg = "_NET_CLIENT_LIST_STACKING"
            clientList =
                get_property(
                    disp, x11.XDefaultRootWindow(disp),
                    X11.XA_WINDOW, "_NET_CLIENT_LIST_STACKING", size,
                )
        } else {
            msg = "_NET_CLIENT_LIST or _WIN_CLIENT_LIST"
            clientList =
                get_property(
                    disp, x11.XDefaultRootWindow(disp),
                    X11.XA_WINDOW, "_NET_CLIENT_LIST", size,
                )
            if (clientList == null) {
                clientList =
                    get_property(
                        disp, x11.XDefaultRootWindow(disp),
                        X11.XA_CARDINAL, "_WIN_CLIENT_LIST", size,
                    )
            }
        }

        if (clientList == null) {
            logger.error { "Cannot get client list properties.\n(msg)" }
            return null
        }

        val resultList = ArrayList<X11.Window>()
        val SIZE_OF_WINDOW = 4
        var i = 0
        val count = size.value.toLong().toInt() / SIZE_OF_WINDOW
        while (i < count) {
            resultList.add(
                X11.Window(
                    Pointer.nativeValue(
                        clientList
                            .getPointer((i * X11.Window.SIZE).toLong()),
                    ),
                ),
            )

            i++
        }

        return resultList
    }

    fun windowToDesktop(
        disp: X11.Display,
        win: X11.Window,
    ): Boolean {
        return windowToDesktop(disp, win, -1)
    }

    fun windowToDesktop(
        disp: X11.Display,
        win: X11.Window,
        desktop: Int,
    ): Boolean {
        var currentDesktop = desktop
        if (currentDesktop < 0) {
            currentDesktop = getCurrentDesktop(disp)
            if (currentDesktop < 0) {
                logger.error {
                    "Cannot get current desktop properties. (_NET_CURRENT_DESKTOP or _WIN_WORKSPACE property)"
                }
                return false
            }
        }

        return client_msg(disp, win, "_NET_WM_DESKTOP", currentDesktop.toLong(), 0, 0, 0, 0)
    }

    fun getCurrentDesktop(disp: X11.Display): Int {
        val root: X11.Window = x11.XDefaultRootWindow(disp)
        var cur_desktop: Int? = null
        if ((
                get_property_as_int(
                    disp, root, X11.XA_CARDINAL,
                    "_NET_CURRENT_DESKTOP",
                ).also { cur_desktop = it }
            ) == null
        ) {
            if ((
                    get_property_as_int(
                        disp, root, X11.XA_CARDINAL,
                        "_WIN_WORKSPACE",
                    ).also { cur_desktop = it }
                ) == null
            ) {
                logger.error {
                    "Cannot get current desktop properties. (_NET_CURRENT_DESKTOP or _WIN_WORKSPACE property)"
                }
            }
        }

        return if ((cur_desktop == null)) -1 else cur_desktop!!
    }

    fun activateWindow(
        disp: X11.Display,
        win: X11.Window,
        switch_desktop: Boolean,
    ): Boolean {
        var desktop: Long? = null

        // desktop ID
        if ((
                get_property_as_int(
                    disp, win, X11.XA_CARDINAL,
                    "_NET_WM_DESKTOP",
                ).also { desktop = it?.toLong() }
            ) == null
        ) {
            if ((
                    get_property_as_int(
                        disp, win, X11.XA_CARDINAL,
                        "_WIN_WORKSPACE",
                    ).also { desktop = it?.toLong() }
                ) == null
            ) {
                logger.info { "Cannot find desktop ID of the window." }
            }
        }

        if (switch_desktop && (desktop != null)) {
            if (!client_msg(
                    disp,
                    x11.XDefaultRootWindow(disp),
                    "_NET_CURRENT_DESKTOP",
                    desktop!!,
                    0,
                    0,
                    0,
                    0,
                )
            ) {
                logger.info { "Cannot switch desktop." }
            }
        }

        client_msg(disp, win, "_NET_ACTIVE_WINDOW", 0, 0, 0, 0, 0)
        x11.XMapRaised(disp, win)

        return true
    }

    fun iconify_window(
        disp: X11.Display?,
        win: X11.Window?,
    ): Boolean {
        return X11Ext.INSTANCE.XIconifyWindow(disp, win, x11.XDefaultScreen(disp)) == TRUE
    }

    fun closeWindow(
        disp: X11.Display?,
        win: X11.Window?,
    ): Boolean {
        return client_msg(disp, win, "_NET_CLOSE_WINDOW", 0, 0, 0, 0, 0)
    }

    fun get_window_icon_name(
        disp: X11.Display?,
        win: X11.Window?,
    ): String? {
        val icon_name_return = PointerByReference()

        if (X11Ext.INSTANCE.XGetIconName(disp, win, icon_name_return) == 0) {
            free(icon_name_return.pointer)
            return null
        }

        return g_strdup(icon_name_return.pointer)
    }

    fun get_active_window_class(disp: X11.Display): String? {
        val win: X11.Window? = get_active_window(disp)
        return if ((win == null)) null else get_window_class(disp, win)
    }

    fun get_window_class(
        disp: X11.Display?,
        win: X11.Window?,
    ): String? {
        var class_utf8: String? = null
        val size = NativeLongByReference()

        val wm_class: Pointer? =
            get_property(
                disp,
                win,
                X11.XA_STRING,
                "WM_CLASS",
                size,
            )
        if (wm_class != null) {
            var p_0 = 0
            while (wm_class.getByte(p_0.toLong()).toInt() != 0) {
                p_0++
            }
            if (size.value.toLong() - 1 > p_0) {
                wm_class.setByte(p_0.toLong(), '.'.code.toByte())
            }
            class_utf8 = g_locale_to_utf8(wm_class)
        }

        free(wm_class)

        return class_utf8
    }

    fun get_active_window_id(disp: X11.Display): Long {
        val win: X11.Window? = get_active_window(disp)
        return if ((win == null)) -1 else get_window_id(win)
    }

    fun get_window_id(win: X11.Window): Long {
        return win.toLong()
    }

    fun get_active_window_pid(disp: X11.Display): Int {
        val win: X11.Window? = get_active_window(disp)
        return if ((win == null)) -1 else get_window_pid(disp, win)
    }

    fun get_window_pid(
        disp: X11.Display,
        win: X11.Window,
    ): Int {
        return get_property_as_int(
            disp, win, X11.XA_CARDINAL,
            "_NET_WM_PID",
        ) ?: -1
    }

    fun get_property(
        disp: X11.Display?,
        win: X11.Window?,
        xa_prop_type: Atom,
        prop_name: String?,
    ): Pointer? {
        return get_property(disp, win, xa_prop_type, prop_name, null)
    }

    fun get_active_window(disp: X11.Display): X11.Window? {
        return get_property_as_window(
            disp,
            x11.XDefaultRootWindow(disp),
            X11.XA_WINDOW,
            "_NET_ACTIVE_WINDOW",
        )
    }

    private fun get_property_as_window(
        disp: X11.Display,
        win: X11.Window?,
        xa_prop_type: Atom,
        prop_name: String,
    ): X11.Window? {
        var ret: X11.Window? = null

        val prop =
            get_property(
                disp,
                win,
                xa_prop_type,
                prop_name,
                null,
            )
        if (prop != null) {
            ret = X11.Window(prop.getLong(0))
            free(prop)
        }

        return ret
    }

    private fun get_property_as_int(
        disp: X11.Display,
        win: X11.Window,
        xa_prop_type: Atom,
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
            free(prop)
        }

        return intProp
    }

    fun get_property(
        disp: X11.Display?,
        win: X11.Window?,
        xa_prop_type: Atom,
        prop_name: String?,
        size: NativeLongByReference?,
    ): Pointer? {
        val xa_ret_type = AtomByReference()
        val ret_format = IntByReference()
        val ret_nitems = NativeLongByReference()
        val ret_bytes_after = NativeLongByReference()
        val ret_prop = PointerByReference()

        val xa_prop_name = x11.XInternAtom(disp, prop_name, false)

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
        if (x11.XGetWindowProperty(
                disp, win, xa_prop_name, NativeLong(0),
                NativeLong(MAX_PROPERTY_VALUE_LEN / 4), false,
                xa_prop_type, xa_ret_type, ret_format, ret_nitems,
                ret_bytes_after, ret_prop,
            ) != X11.Success
        ) {
            logger.error { "Cannot get $prop_name property." }
            return null
        }

        if ((xa_ret_type.value == null) ||
            (
                xa_ret_type.value.toLong() !=
                    xa_prop_type
                        .toLong()
            )
        ) {
            logger.error { "Invalid type of $prop_name property." }
            free(ret_prop.pointer)
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

    fun client_msg(
        disp: X11.Display?,
        win: X11.Window?,
        msg: String?,
        data0: Long,
        data1: Long,
        data2: Long,
        data3: Long,
        data4: Long,
    ): Boolean {
        val mask =
            NativeLong(
                (
                    X11.SubstructureRedirectMask
                        or X11.SubstructureNotifyMask
                ).toLong(),
            )

        val xclient = XClientMessageEvent()
        xclient.type = X11.ClientMessage
        xclient.serial = NativeLong(0)
        xclient.send_event = TRUE
        xclient.message_type = x11.XInternAtom(disp, msg, false)
        xclient.window = win
        xclient.format = 32
        xclient.data.setType(Array<NativeLong>::class.java)
        xclient.data.l[0] = NativeLong(data0)
        xclient.data.l[1] = NativeLong(data1)
        xclient.data.l[2] = NativeLong(data2)
        xclient.data.l[3] = NativeLong(data3)
        xclient.data.l[4] = NativeLong(data4)

        val event = XEvent()
        event.setTypedValue(xclient)

        if (x11.XSendEvent(
                disp, x11.XDefaultRootWindow(disp), FALSE, mask,
                event,
            ) != FALSE
        ) {
            return EXIT_SUCCESS
        } else {
            logger.error { "Cannot send $msg event." }
            return EXIT_FAILURE
        }
    }

    private fun g_locale_to_utf8(pointer: Pointer): String {
        return g_strdup(pointer)
    }

    private fun g_strdup(pointer: Pointer): String {
        val value = pointer.getString(0)
        return value
    }

    private fun free(pointer: Pointer?) {
        if (pointer != null) {
            x11.XFree(pointer)
        }
    }
}

private interface X11Ext : Library {

    fun XMoveWindow(
        disp: X11.Display?,
        win: X11.Window?,
        x: Long,
        y: Long,
    )

    fun XResizeWindow(
        disp: X11.Display?,
        win: X11.Window?,
        width: Long,
        height: Long,
    )

    fun XMoveResizeWindow(
        disp: X11.Display?,
        win: X11.Window?,
        x: Long,
        y: Long,
        width: Long,
        height: Long,
    )

    fun XCreateFontCursor(
        disp: X11.Display?,
        shape: Long,
    ): X11.Cursor?

    fun XGrabPointer(
        disp: X11.Display?,
        grab_window: X11.Window?,
        owner_events: Int,
        event_mask: NativeLong?,
        pointer_mode: Int,
        keyboard_mode: Int,
        confine_to: X11.Window?,
        cursor: X11.Cursor?,
        time: Int,
    ): Int

    fun XAllowEvents(
        disp: X11.Display?,
        event_mode: Int,
        time: Int,
    ): Int

    fun XUngrabPointer(
        disp: X11.Display?,
        time: Int,
    ): Int

    fun XmuClientWindow(
        disp: X11.Display?,
        win: X11.Window?,
    ): X11.Window?

    fun XGetIconName(
        disp: X11.Display?,
        win: X11.Window?,
        icon_name_return: PointerByReference?,
    ): Int

    fun XIconifyWindow(
        disp: X11.Display?,
        win: X11.Window?,
        screen: Int,
    ): Int

    companion object {
        const val XC_crosshair: Int = 34

        val INSTANCE: X11Ext = Native.load("X11", X11Ext::class.java)
    }
}

class Options {
    var verbose: Boolean = false
    var force_utf8: Boolean = false
    var show_class: Boolean = false
    var show_pid: Boolean = false
    var show_geometry: Boolean = false
    var stacking_order: Boolean = false
    var match_by_id: Boolean = false
    var match_by_cls: Boolean = false
    var full_window_title_match: Boolean = false
    var wa_desktop_titles_invalid_utf8: Boolean = false
    var param_window: String = ""
    var param: String = ""
}
