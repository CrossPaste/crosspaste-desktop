package com.crosspaste.platform.linux.api

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.platform.unix.X11
import com.sun.jna.platform.unix.X11.AtomByReference
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.NativeLongByReference
import com.sun.jna.ptr.PointerByReference
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.ByteArrayOutputStream

/**
 * Reads the `text/html` payload of the X11 `CLIPBOARD` selection as **raw
 * bytes**, bypassing AWT's charset handling.
 *
 * AWT exposes `text/html` to consumers only through `charset=Unicode` (UTF-16)
 * String/Reader flavors and, on Linux, picks the source target with logic that
 * can mangle non-ASCII text (e.g. IntelliJ / JBR html copied as CJK arrives as
 * U+FFFD garbage). To recover, we enumerate the selection's real `TARGETS`,
 * pick the html target whose encoding preserves every character, read its bytes
 * ourselves, and decode them downstream with a charset we actually know.
 *
 * Toolkits advertise html differently:
 *  - IntelliJ / JBR (AWT source) offer only charset-tagged variants such as
 *    `text/html;charset=UTF-16` / `;charset=ISO-8859-1` — there is **no** bare
 *    `text/html` and no UTF-8 variant, so a UTF-16 target is the lossless pick.
 *  - Browsers / GTK / Qt typically offer a bare `text/html` carrying UTF-8.
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

    // Upper bound on the total accumulated payload; anything larger is not a
    // plausible html clipboard and falls back to AWT's value instead of
    // ballooning the heap.
    private const val MAX_TOTAL_BYTES = 64L * 1024 * 1024

    private val CHARSET_PARAM = Regex("""charset\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE)

    // ICCCM prefers the timestamp of the user event that triggered the request,
    // but this reader runs outside any X event stream, so CurrentTime is the
    // only option (xclip/xsel do the same); owners accept it in practice.
    private val NO_EVENT_TIMESTAMP = NativeLong(X11.CurrentTime.toLong())

    /**
     * JNA's bundled [X11] interface declares `XGetAtomName` as returning a Java
     * `String`, which copies the native `char*` but never `XFree`s it, leaking a
     * few bytes per call. This minimal re-binding returns the raw [Pointer] so
     * [atomName] can free it.
     */
    private interface X11AtomNameApi : Library {
        fun XGetAtomName(
            display: X11.Display,
            atom: X11.Atom,
        ): Pointer?

        companion object {
            val INSTANCE: X11AtomNameApi = Native.load("X11", X11AtomNameApi::class.java)
        }
    }

    /**
     * Raw clipboard html bytes plus the charset we already know from the chosen
     * target name ([charsetName] is null for a bare `text/html` target, where
     * the caller must detect the encoding instead).
     */
    class HtmlBytes(
        val bytes: ByteArray,
        val charsetName: String?,
    )

    /**
     * Reads the best available `text/html` target of the `CLIPBOARD` selection.
     * Returns null if there is no owner, no usable html target, the data arrives
     * via INCR, or anything fails — in every such case the caller keeps AWT's
     * (possibly mojibaked) value.
     */
    fun readClipboardHtml(): HtmlBytes? {
        val x11 = X11Api.INSTANCE
        val display = x11.XOpenDisplay(null)
        if (display == null) {
            logger.warn { "readClipboardHtml: XOpenDisplay returned null" }
            return null
        }
        return try {
            readClipboardHtml(x11, display)
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to read X11 clipboard html" }
            null
        } finally {
            x11.XCloseDisplay(display)
        }
    }

    private fun readClipboardHtml(
        x11: X11Api,
        display: X11.Display,
    ): HtmlBytes? {
        val clipboard = x11.XInternAtom(display, "CLIPBOARD", false)
        val selectionOwner = x11.XGetSelectionOwner(display, clipboard)
        if (selectionOwner == null) {
            logger.warn { "readClipboardHtml: CLIPBOARD has no selection owner" }
            return null
        }

        val root = x11.XDefaultRootWindow(display)
        val window = x11.XCreateSimpleWindow(display, root, 0, 0, 1, 1, 0, 0, 0)
        return try {
            x11.XSelectInput(display, window, NativeLong(X11.PropertyChangeMask.toLong()))

            val targets = queryTargets(x11, display, window, clipboard)
            logger.debug { "Clipboard TARGETS (${targets.size}): $targets" }

            val htmlTarget = chooseHtmlTarget(targets)
            if (htmlTarget == null) {
                logger.warn { "readClipboardHtml: no usable text/html target among $targets" }
                return null
            }
            logger.debug { "readClipboardHtml: requesting target '$htmlTarget'" }

            val bytes = convertAndReadBytes(x11, display, window, clipboard, htmlTarget) ?: return null
            HtmlBytes(bytes, htmlTargetCharset(htmlTarget))
        } finally {
            x11.XDestroyWindow(display, window)
        }
    }

    /**
     * Picks the html target whose encoding best preserves the original text.
     * Prefers UTF-8, then a bare `text/html` (detected downstream, usually
     * UTF-8), then a lossless UTF-16/32 variant, and only falls back to a legacy
     * single-byte charset when nothing better is offered.
     */
    private fun chooseHtmlTarget(targets: List<String>): String? {
        var best: String? = null
        var bestScore = Int.MIN_VALUE
        for (target in targets) {
            val lower = target.lowercase()
            if (lower != "text/html" && !lower.startsWith("text/html;")) {
                continue
            }
            val charset = htmlTargetCharset(target)
            val score =
                when {
                    charset == null -> 90
                    charset == "utf-8" -> 100
                    charset == "utf-16" || charset == "unicode" -> 82
                    charset == "utf-16le" || charset == "utf-16be" -> 80
                    charset.startsWith("utf-32") -> 70
                    else -> 10
                }
            if (score > bestScore) {
                bestScore = score
                best = target
            }
        }
        return best
    }

    private fun htmlTargetCharset(target: String): String? {
        val semicolon = target.indexOf(';')
        if (semicolon < 0) return null
        return CHARSET_PARAM
            .find(target.substring(semicolon + 1))
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.lowercase()
    }

    /** Enumerates the `TARGETS` the selection owner advertises. */
    private fun queryTargets(
        x11: X11Api,
        display: X11.Display,
        window: X11.Window,
        clipboard: X11.Atom,
    ): List<String> {
        val targets = x11.XInternAtom(display, "TARGETS", false)
        val property = x11.XInternAtom(display, "CROSSPASTE_TARGETS", false)
        x11.XConvertSelection(display, clipboard, targets, property, window, NO_EVENT_TIMESTAMP)
        x11.XFlush(display)

        val notify = waitForSelectionNotify(x11, display, window) ?: return emptyList()
        if (notify.property == null || notify.property.toLong() == 0L) {
            logger.warn { "TARGETS request returned property=None" }
            return emptyList()
        }

        val actualType = AtomByReference()
        val actualFormat = IntByReference()
        val nItems = NativeLongByReference()
        val bytesAfter = NativeLongByReference()
        val prop = PointerByReference()
        val status =
            x11.XGetWindowProperty(
                display,
                window,
                notify.property,
                NativeLong(0),
                NativeLong(1024),
                false,
                X11.Atom(X11.AnyPropertyType.toLong()),
                actualType,
                actualFormat,
                nItems,
                bytesAfter,
                prop,
            )
        if (status != X11.Success || prop.value == null) {
            logger.warn { "TARGETS XGetWindowProperty failed status=$status" }
            return emptyList()
        }
        return try {
            val count = nItems.value.toInt()
            // format-32 atom list is returned as an array of C long.
            val atomIds =
                when (Native.LONG_SIZE) {
                    java.lang.Long.BYTES -> prop.value.getLongArray(0, count).toList()
                    Integer.BYTES -> prop.value.getIntArray(0, count).map { it.toLong() }
                    else -> emptyList()
                }
            atomIds.mapNotNull { id ->
                if (id == 0L) {
                    null
                } else {
                    atomName(x11, display, X11.Atom(id))
                }
            }
        } finally {
            prop.value?.let { x11.XFree(it) }
            x11.XDeleteProperty(display, window, notify.property)
        }
    }

    /** Converts the selection into [targetName] and reads the resulting bytes. */
    private fun convertAndReadBytes(
        x11: X11Api,
        display: X11.Display,
        window: X11.Window,
        clipboard: X11.Atom,
        targetName: String,
    ): ByteArray? {
        val target = x11.XInternAtom(display, targetName, false)
        val property = x11.XInternAtom(display, "CROSSPASTE_CLIPBOARD", false)

        x11.XConvertSelection(display, clipboard, target, property, window, NO_EVENT_TIMESTAMP)
        x11.XFlush(display)

        val notify = waitForSelectionNotify(x11, display, window) ?: return null
        // property == None means the owner could not provide the target.
        if (notify.property == null || notify.property.toLong() == 0L) {
            logger.warn { "convertAndReadBytes('$targetName'): owner returned property=None" }
            return null
        }

        val incr = x11.XInternAtom(display, "INCR", false)
        val bytes = readProperty(x11, display, window, notify.property, incr)
        logger.debug { "convertAndReadBytes('$targetName'): read ${bytes?.size ?: -1} bytes" }
        return bytes
    }

    /** Resolves an atom's name, freeing the native string JNA would otherwise leak. */
    private fun atomName(
        x11: X11Api,
        display: X11.Display,
        atom: X11.Atom,
    ): String? =
        runCatching {
            X11AtomNameApi.INSTANCE.XGetAtomName(display, atom)?.let { ptr ->
                try {
                    ptr.getString(0)
                } finally {
                    x11.XFree(ptr)
                }
            }
        }.getOrNull()

    private fun waitForSelectionNotify(
        x11: X11Api,
        display: X11.Display,
        window: X11.Window,
    ): X11.XSelectionEvent? {
        val event = X11.XEvent()
        // Monotonic clock: a wall-clock (NTP) jump must not stretch or cut the wait.
        val deadlineNanos = System.nanoTime() + SELECTION_TIMEOUT_MS * 1_000_000
        while (System.nanoTime() < deadlineNanos) {
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
        val result = ByteArrayOutputStream()
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
                if (result.size().toLong() + chunk.size > MAX_TOTAL_BYTES) {
                    logger.warn {
                        "Clipboard html exceeds $MAX_TOTAL_BYTES bytes; skipping native read"
                    }
                    return null
                }
                result.write(chunk)

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
        return if (result.size() == 0) null else result.toByteArray()
    }
}
