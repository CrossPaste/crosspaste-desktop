package com.clipevery.os.linux.api

import com.clipevery.os.linux.api.X11Api.Companion.INSTANCE
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
import java.awt.image.BufferedImage

object WMCtrl {

    private const val MAX_PROPERTY_VALUE_LEN: Long = 8 * 1024 * 1024

    private val logger = KotlinLogging.logger {}

    private val x11 = Native.load("X11", X11::class.java)

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

            val wPid: Int = getWindowPid(display, window)
            if (wPid == findPid) {
                return window
            }

            x11.XFree(name.pointer)

            val rec: X11.Window? = findWindow(x11, display, window, depth + 1, findPid)
            if (rec != null) return rec
        }

        return null
    }

    fun findWindowByTitle(
        display: X11.Display,
        rootWindow: X11.Window,
        title: String,
    ): X11.Window? {
        val x11 = INSTANCE

        val windowRef = X11.WindowByReference()
        val parentRef = X11.WindowByReference()
        val childrenRef = PointerByReference()
        val childCountRef = IntByReference()

        x11.XQueryTree(display, rootWindow, windowRef, parentRef, childrenRef, childCountRef)
        if (childrenRef.value == null) {
            return null
        }

        val childrenCount = childCountRef.value
        if (childrenCount == 0) return null

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

            val namePtr = PointerByReference()
            if (INSTANCE.XFetchName(display, window, namePtr) != 0) {
                val name = namePtr.value.getString(0)
                free(namePtr.value)
                if (title == name) {
                    return window
                }
            } else {
                free(namePtr.value)
            }
        }
        return null
    }

    fun switchDesktop(
        display: X11.Display,
        target: Long,
    ): Boolean {
        if (target < 0) {
            logger.error { "Invalid desktop ID." }
            return false
        }
        if (clientMsg(
                display,
                x11.XDefaultRootWindow(display),
                "_NET_CURRENT_DESKTOP",
                target,
                0,
                0,
                0,
                0,
            )
        ) {
            x11.XFlush(display)
            return true
        }
        return false
    }

    fun windowToDesktop(
        display: X11.Display,
        win: X11.Window,
    ): Boolean {
        return windowToDesktop(display, win, -1)
    }

    fun windowToDesktop(
        display: X11.Display,
        win: X11.Window,
        desktop: Int,
    ): Boolean {
        var currentDesktop = desktop
        if (currentDesktop < 0) {
            currentDesktop = getCurrentDesktop(display)
            if (currentDesktop < 0) {
                logger.error {
                    "Cannot get current desktop properties. (_NET_CURRENT_DESKTOP or _WIN_WORKSPACE property)"
                }
                return false
            }
        }

        return clientMsg(display, win, "_NET_WM_DESKTOP", currentDesktop.toLong(), 0, 0, 0, 0)
    }

    fun getCurrentDesktop(display: X11.Display): Int {
        val root: X11.Window = x11.XDefaultRootWindow(display)
        var curDesktop: Int? = null
        if ((
                getPropertyAsInt(
                    display, root,
                    "_NET_CURRENT_DESKTOP",
                ).also { curDesktop = it }
            ) == null
        ) {
            if ((
                    getPropertyAsInt(
                        display, root,
                        "_WIN_WORKSPACE",
                    ).also { curDesktop = it }
                ) == null
            ) {
                logger.error {
                    "Cannot get current desktop properties. (_NET_CURRENT_DESKTOP or _WIN_WORKSPACE property)"
                }
            }
        }

        return if ((curDesktop == null)) -1 else curDesktop!!
    }

    fun activeWindow(
        display: X11.Display,
        win: X11.Window,
        switchDesktop: Boolean,
    ): Boolean {
        var desktop: Long? = null

        // desktop ID
        if ((
                getPropertyAsInt(
                    display, win,
                    "_NET_WM_DESKTOP",
                ).also { desktop = it?.toLong() }
            ) == null
        ) {
            if ((
                    getPropertyAsInt(
                        display, win,
                        "_WIN_WORKSPACE",
                    ).also { desktop = it?.toLong() }
                ) == null
            ) {
                logger.info { "Cannot find desktop ID of the window." }
            }
        }

        if (switchDesktop && (desktop != null)) {
            if (!clientMsg(
                    display,
                    x11.XDefaultRootWindow(display),
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

        clientMsg(display, win, "_NET_ACTIVE_WINDOW", 0, 0, 0, 0, 0)
        x11.XMapRaised(display, win)

        return true
    }

    fun iconifyWindow(
        display: X11.Display?,
        win: X11.Window?,
    ): Boolean {
        return X11Ext.INSTANCE.XIconifyWindow(display, win, x11.XDefaultScreen(display)) == TRUE
    }

    fun closeWindow(
        display: X11.Display?,
        win: X11.Window?,
    ): Boolean {
        return clientMsg(display, win, "_NET_CLOSE_WINDOW", 0, 0, 0, 0, 0)
    }

    fun getWindowIconName(
        display: X11.Display,
        win: X11.Window,
    ): String? {
        val iconNameReturn = PointerByReference()

        if (X11Ext.INSTANCE.XGetIconName(display, win, iconNameReturn) == 0) {
            free(iconNameReturn.pointer)
            return null
        }

        return g_strdup(iconNameReturn.pointer)
    }

    fun getActiveWindowInstanceAndClass(display: X11.Display): Pair<String?, String?>? {
        return getActiveWindow(display)?.let {
            return getWindowClass(display, it)
        }
    }

    fun getWindowClass(
        display: X11.Display,
        win: X11.Window,
    ): Pair<String, String>? {
        val size = NativeLongByReference()

        return getProperty(
            display,
            win,
            X11.XA_STRING,
            "WM_CLASS",
            size,
        )?.let { pointer ->
            val instanceName: String? = pointer.getString(0, Native.getDefaultStringEncoding())

            return instanceName?.let {
                val endOfInstanceName: Long = instanceName.length.toLong()
                val className = pointer.getString(endOfInstanceName + 1, Native.getDefaultStringEncoding())

                free(pointer)
                return Pair(instanceName, className)
            }
        }
    }

    fun getActiveWindowId(display: X11.Display): Long {
        return getActiveWindow(display)?.let {
            return getWindowId(it)
        } ?: -1
    }

    fun getWindowId(win: X11.Window): Long {
        return win.toLong()
    }

    fun getActiveWindowPid(display: X11.Display): Int {
        return getActiveWindow(display)?.let {
            return getWindowPid(display, it)
        } ?: -1
    }

    fun getWindowPid(
        display: X11.Display,
        win: X11.Window,
    ): Int {
        return getPropertyAsInt(
            display, win,
            "_NET_WM_PID",
        ) ?: -1
    }

    fun getProperty(
        display: X11.Display,
        win: X11.Window,
        xaPropType: Atom,
        propName: String?,
    ): Pointer? {
        return getProperty(display, win, xaPropType, propName, null)
    }

    fun getActiveWindow(display: X11.Display): X11.Window? {
        return getPropertyAsWindow(
            display,
            x11.XDefaultRootWindow(display),
        )
    }

    fun getPropertyAsIcon(
        display: X11.Display,
        window: X11.Window,
    ): BufferedImage? {
        return getProperty(
            display,
            window,
            X11.XA_CARDINAL,
            "_NET_WM_ICON",
            null,
        )?.let { prop ->
            val width = prop.getLong(0).toInt()
            val height = prop.getLong(8).toInt()
            val buffer = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

            val numPixels = width * height
            val bytesPerPixel = 8 // 每个像素8字节，包括填充
            val imageData = ByteArray(numPixels * bytesPerPixel)

            prop.read(16, imageData, 0, imageData.size)

            // https://stackoverflow.com/questions/43237104/picture-format-for-net-wm-icon
            var offset = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val bgraIndex = offset
                    val blue = imageData[bgraIndex].toInt() and 0xff
                    val green = imageData[bgraIndex + 1].toInt() and 0xff
                    val red = imageData[bgraIndex + 2].toInt() and 0xff
                    val alpha = imageData[bgraIndex + 3].toInt() and 0xff
                    val argb = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
                    buffer.setRGB(x, y, argb)
                    offset += bytesPerPixel
                }
            }
            return buffer
        }
    }

    private fun getPropertyAsWindow(
        display: X11.Display,
        window: X11.Window,
    ): X11.Window? {
        return getPropertyAs(
            display,
            window,
            X11.XA_WINDOW,
            "_NET_ACTIVE_WINDOW",
        ) { pointer ->
            X11.Window(pointer.getLong(0))
        }
    }

    private fun getPropertyAsInt(
        display: X11.Display,
        window: X11.Window,
        propName: String,
    ): Int? {
        return getPropertyAs(
            display,
            window,
            X11.XA_CARDINAL,
            propName,
        ) { pointer ->
            pointer.getInt(0)
        }
    }

    private fun <T> getPropertyAs(
        display: X11.Display,
        window: X11.Window,
        xaPropType: Atom,
        propName: String,
        convertTo: (Pointer) -> T,
    ): T? {
        return getProperty(
            display,
            window,
            xaPropType,
            propName,
            null,
        )?.let { pointer ->
            val result = convertTo(pointer)
            free(pointer)
            return result
        }
    }

    fun getProperty(
        display: X11.Display,
        window: X11.Window,
        xaPropType: Atom,
        propName: String?,
        size: NativeLongByReference?,
    ): Pointer? {
        val xaRetType = AtomByReference()
        val retFormat = IntByReference()
        val retNitems = NativeLongByReference()
        val retBytesAfter = NativeLongByReference()
        val retProp = PointerByReference()

        val xaPropName = x11.XInternAtom(display, propName, false)

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
                display, window, xaPropName, NativeLong(0),
                NativeLong(MAX_PROPERTY_VALUE_LEN / 4), false,
                xaPropType, xaRetType, retFormat, retNitems,
                retBytesAfter, retProp,
            ) != X11.Success
        ) {
            logger.error { "Cannot get $propName property." }
            return null
        }

        if ((xaRetType.value == null) ||
            (
                xaRetType.value.toLong() !=
                    xaPropType
                        .toLong()
            )
        ) {
            logger.error { "Invalid type of $propName property." }
            free(retProp.pointer)
            return null
        }

        if (size != null) {
            var tmp_size = (
                (retFormat.value / 8) *
                    retNitems.value.toLong()
            )
            // Correct 64 Architecture implementation of 32 bit data
            if (retFormat.value == 32) {
                tmp_size *= (NativeLong.SIZE / 4).toLong()
            }
            size.value = NativeLong(tmp_size)
        }

        return retProp.value
    }

    private fun clientMsg(
        display: X11.Display?,
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
        xclient.message_type = x11.XInternAtom(display, msg, false)
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
                display, x11.XDefaultRootWindow(display), FALSE, mask,
                event,
            ) != FALSE
        ) {
            return true
        } else {
            logger.error { "Cannot send $msg event." }
            return false
        }
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
        display: X11.Display?,
        win: X11.Window?,
        x: Long,
        y: Long,
    )

    fun XResizeWindow(
        display: X11.Display?,
        win: X11.Window?,
        width: Long,
        height: Long,
    )

    fun XMoveResizeWindow(
        display: X11.Display?,
        win: X11.Window?,
        x: Long,
        y: Long,
        width: Long,
        height: Long,
    )

    fun XCreateFontCursor(
        display: X11.Display?,
        shape: Long,
    ): X11.Cursor?

    fun XGrabPointer(
        display: X11.Display?,
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
        display: X11.Display?,
        event_mode: Int,
        time: Int,
    ): Int

    fun XUngrabPointer(
        display: X11.Display?,
        time: Int,
    ): Int

    fun XmuClientWindow(
        display: X11.Display?,
        win: X11.Window?,
    ): X11.Window?

    fun XGetIconName(
        display: X11.Display?,
        win: X11.Window?,
        icon_name_return: PointerByReference?,
    ): Int

    fun XIconifyWindow(
        display: X11.Display?,
        win: X11.Window?,
        screen: Int,
    ): Int

    companion object {
        val INSTANCE: X11Ext = Native.load("X11", X11Ext::class.java)
    }
}
