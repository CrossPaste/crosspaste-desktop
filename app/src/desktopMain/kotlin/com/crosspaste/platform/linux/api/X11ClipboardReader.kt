package com.crosspaste.platform.linux.api

import com.sun.jna.NativeLong
import com.sun.jna.platform.unix.X11
import com.sun.jna.platform.unix.X11.AtomByReference
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.NativeLongByReference
import com.sun.jna.ptr.PointerByReference
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Reads a single target of the X11 `CLIPBOARD` selection as **raw bytes**,
 * bypassing AWT's charset handling.
 *
 * AWT exposes `text/html` only through `charset=Unicode` (UTF-16) String/Reader
 * flavors. When a source (IntelliJ / JBR, Qt, …) actually publishes that target
 * as UTF-8 — or any other encoding — AWT decodes it with the wrong charset and
 * the result is irreversibly mangled (non-ASCII bytes can collapse into
 * U+FFFD). To recover, we must read the selection bytes ourselves and detect
 * the real encoding downstream.
 *
 * This performs a standard ICCCM selection transfer on its own display
 * connection: convert the selection into a property on a throwaway window, wait
 * for `SelectionNotify`, then read the property. Large `INCR` transfers are not
 * handled and fall back to null (the caller keeps AWT's value).
 */
object X11ClipboardReader {

    private val logger = KotlinLogging.logger {}

    private const val SELECTION_TIMEOUT_MS = 1000L

    // long_length for XGetWindowProperty is in 32-bit units; cap a single read
    // at 16 MiB of payload, looping on bytesAfter for anything larger.
    private const val MAX_READ_LONGS = (16L * 1024 * 1024) / 4

    /**
     * Returns the raw bytes of [targetMime] (e.g. `text/html`) from the
     * `CLIPBOARD` selection, or null if there is no owner, the owner does not
     * offer the target, the data arrives via INCR, or anything fails.
     */
    fun readClipboardTargetBytes(targetMime: String): ByteArray? {
        val x11 = X11Api.INSTANCE
        val display = x11.XOpenDisplay(null) ?: return null
        return try {
            readClipboardTargetBytes(x11, display, targetMime)
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to read X11 selection target '$targetMime'" }
            null
        } finally {
            x11.XCloseDisplay(display)
        }
    }

    private fun readClipboardTargetBytes(
        x11: X11Api,
        display: X11.Display,
        targetMime: String,
    ): ByteArray? {
        val clipboard = x11.XInternAtom(display, "CLIPBOARD", false)
        if (x11.XGetSelectionOwner(display, clipboard) == null) {
            return null
        }

        val root = x11.XDefaultRootWindow(display)
        val window = x11.XCreateSimpleWindow(display, root, 0, 0, 1, 1, 0, 0, 0)
        return try {
            x11.XSelectInput(display, window, NativeLong(X11.PropertyChangeMask.toLong()))

            val target = x11.XInternAtom(display, targetMime, false)
            val property = x11.XInternAtom(display, "CROSSPASTE_CLIPBOARD", false)

            x11.XConvertSelection(display, clipboard, target, property, window, NativeLong(X11.CurrentTime.toLong()))
            x11.XFlush(display)

            val notify = waitForSelectionNotify(x11, display, window) ?: return null
            // property == None means the owner could not provide the target.
            if (notify.property == null || notify.property.toLong() == 0L) {
                return null
            }

            val incr = x11.XInternAtom(display, "INCR", false)
            readProperty(x11, display, window, notify.property, incr)
        } finally {
            x11.XDestroyWindow(display, window)
        }
    }

    private fun waitForSelectionNotify(
        x11: X11Api,
        display: X11.Display,
        window: X11.Window,
    ): X11.XSelectionEvent? {
        val event = X11.XEvent()
        val deadline = System.currentTimeMillis() + SELECTION_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (x11.XCheckTypedWindowEvent(display, window, X11.SelectionNotify, event)) {
                event.setType(X11.XSelectionEvent::class.java)
                event.read()
                return event.xselection
            }
            Thread.sleep(5L)
        }
        logger.warn { "Timed out waiting for SelectionNotify" }
        return null
    }

    private fun readProperty(
        x11: X11Api,
        display: X11.Display,
        window: X11.Window,
        property: X11.Atom,
        incr: X11.Atom,
    ): ByteArray? {
        val result = ArrayList<Byte>()
        var offsetLongs = 0L
        while (true) {
            val actualType = AtomByReference()
            val actualFormat = IntByReference()
            val nItems = NativeLongByReference()
            val bytesAfter = NativeLongByReference()
            val prop = PointerByReference()

            val status =
                x11.XGetWindowProperty(
                    display,
                    window,
                    property,
                    NativeLong(offsetLongs),
                    NativeLong(MAX_READ_LONGS),
                    false,
                    X11.Atom(X11.AnyPropertyType.toLong()),
                    actualType,
                    actualFormat,
                    nItems,
                    bytesAfter,
                    prop,
                )

            if (status != X11.Success) {
                return null
            }

            try {
                val type = actualType.value
                if (type != null && incr.toLong() == type.toLong()) {
                    logger.warn { "Clipboard target uses INCR transfer; skipping native read" }
                    return null
                }

                val format = actualFormat.value
                val count = nItems.value.toLong()
                val pointer = prop.value
                if (format != 8 || count <= 0L || pointer == null) {
                    break
                }

                val chunk = pointer.getByteArray(0, count.toInt())
                result.ensureCapacity(result.size + chunk.size)
                for (b in chunk) {
                    result.add(b)
                }

                // long_offset advances in 32-bit units; format-8 data packs 4
                // bytes per unit.
                offsetLongs += (chunk.size + 3) / 4
                if (bytesAfter.value.toLong() <= 0L) {
                    break
                }
            } finally {
                prop.value?.let { x11.XFree(it) }
            }
        }

        x11.XDeleteProperty(display, window, property)
        return if (result.isEmpty()) null else result.toByteArray()
    }
}
