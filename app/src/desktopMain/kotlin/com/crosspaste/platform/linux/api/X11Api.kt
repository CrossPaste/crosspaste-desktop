package com.crosspaste.platform.linux.api

import com.crosspaste.app.LinuxAppInfo
import com.crosspaste.platform.linux.api.WMCtrl.getActiveWindow
import com.crosspaste.utils.pollUntil
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.platform.unix.X11
import com.sun.jna.platform.unix.X11.Display
import com.sun.jna.platform.unix.X11.Window
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.NativeLongByReference
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

interface X11Api : X11 {

    // libX11 selection APIs that JNA's bundled X11 interface does not declare;
    // bound by symbol name from libX11 at load time.
    fun XConvertSelection(
        display: Display,
        selection: X11.Atom,
        target: X11.Atom,
        property: X11.Atom,
        requestor: Window,
        time: NativeLong,
    ): Int

    fun XGetSelectionOwner(
        display: Display,
        selection: X11.Atom,
    ): Window?

    fun XGetInputFocus(
        display: Display,
        focusReturn: X11.WindowByReference,
        revertToReturn: IntByReference,
    ): Int

    // RESOURCE_MANAGER string of the root window (xrdb database, e.g. Xft.dpi);
    // owned by Xlib, must not be freed.
    fun XResourceManagerString(display: Display): Pointer?

    companion object {

        private val logger = KotlinLogging.logger {}

        val INSTANCE: X11Api = Native.load("X11", X11Api::class.java)

        private val ACTIVATION_TIMEOUT = 500.milliseconds

        private val ACTIVATION_POLL_INTERVAL = 10.milliseconds

        private val KEY_HOLD = 100.milliseconds

        /**
         * Source indication used for the `_NET_ACTIVE_WINDOW` requests CrossPaste issues
         * on the user's behalf (pasting back into the previous app, returning focus).
         *
         * Window managers apply focus stealing prevention to `APPLICATION` (1) requests.
         * KWin X11 ignores them even when the request carries the requesting window or a
         * recent timestamp, which leaves the keystroke in CrossPaste's own window. The
         * `PAGER` (2) indication is treated as a trusted activation, and the user did ask
         * for it by picking an item. `focusMainWindow`/`focusSearchWindow` already use it
         * for shortcut-triggered activation.
         */
        private val ACTIVATION_SOURCE: NativeLong = NativeLong(2)

        /**
         * True when a real X11 window holds the input focus.
         *
         * In a Wayland session the compositor withdraws the XWayland input focus
         * while a native Wayland surface is focused, leaving None/PointerRoot.
         * `_NET_ACTIVE_WINDOW` keeps pointing at the last X client in that state,
         * so anything acting on "the focused window" — paste-back targeting,
         * source attribution — has to check this first.
         */
        fun hasXInputFocus(display: Display): Boolean {
            val focusReturn = X11.WindowByReference()
            val revertToReturn = IntByReference()
            INSTANCE.XGetInputFocus(display, focusReturn, revertToReturn)
            // None (0) and PointerRoot (1) mean no real X window holds the focus.
            return (focusReturn.value?.toLong() ?: 0L) > 1L
        }

        /**
         * The focused app as seen through X11, or null when it cannot be named.
         *
         * With [requireXInputFocus] the answer is also null while a native Wayland
         * window is focused, instead of the stale `_NET_ACTIVE_WINDOW` value.
         */
        fun getActiveWindow(requireXInputFocus: Boolean = false): LinuxAppInfo? {
            val display = INSTANCE.XOpenDisplay(null) ?: return null
            return try {
                if (requireXInputFocus && !hasXInputFocus(display)) {
                    null
                } else {
                    getActiveWindow(display)?.let { previousWindow ->
                        WMCtrl.getWindowClass(display, previousWindow)?.let {
                            LinuxAppInfo(previousWindow, it.second)
                        }
                    }
                }
            } finally {
                INSTANCE.XCloseDisplay(display)
            }
        }

        fun saveAppIcon(
            window: Window,
            iconPath: Path,
        ) {
            val display = INSTANCE.XOpenDisplay(null) ?: return
            try {
                WMCtrl.getPropertyAsIcon(display, window)?.let { buffer ->
                    ImageIO.write(buffer, "png", iconPath.toFile())
                }
            } finally {
                INSTANCE.XCloseDisplay(display)
            }
        }

        fun bringToFront(
            window: Window?,
            source: NativeLong = NativeLong(1),
            xServerTime: NativeLong? = null,
        ) {
            val display = INSTANCE.XOpenDisplay(null) ?: return
            try {
                window?.let { window ->
                    WMCtrl.activeWindow(display, window, source, xServerTime ?: NativeLong(X11.CurrentTime.toLong()))
                }
            } finally {
                INSTANCE.XCloseDisplay(display)
            }
        }

        fun bringToBack(
            prevLinuxAppInfo: LinuxAppInfo?,
            source: NativeLong = ACTIVATION_SOURCE,
            xServerTime: NativeLong? = null,
        ) {
            val display = INSTANCE.XOpenDisplay(null) ?: return
            try {
                prevLinuxAppInfo?.let {
                    WMCtrl.activeWindow(display, it.window, source, xServerTime ?: NativeLong(X11.CurrentTime.toLong()))
                }
            } finally {
                INSTANCE.XCloseDisplay(display)
            }
        }

        /**
         * Requests activation of [prevLinuxAppInfo] and waits until the window manager
         * reports it as the active window.
         *
         * XTest delivers the keystroke to whichever window holds the focus at that
         * moment, which right after the request is still CrossPaste's own window. A
         * `false` result means the window manager never confirmed the activation (it
         * may have refused the request), so the caller must not inject blindly.
         */
        suspend fun activateAndAwait(
            prevLinuxAppInfo: LinuxAppInfo,
            source: NativeLong = ACTIVATION_SOURCE,
            xServerTime: NativeLong? = null,
            timeout: Duration = ACTIVATION_TIMEOUT,
        ): Boolean {
            val display = INSTANCE.XOpenDisplay(null) ?: return false
            return try {
                WMCtrl.activeWindow(
                    display,
                    prevLinuxAppInfo.window,
                    source,
                    xServerTime ?: NativeLong(X11.CurrentTime.toLong()),
                )
                awaitActiveOn(display, prevLinuxAppInfo.window, timeout)
            } finally {
                INSTANCE.XCloseDisplay(display)
            }
        }

        /**
         * Waits for [prevLinuxAppInfo] to become the active window without requesting
         * activation. Used after hiding our own window, which is what makes the window
         * manager hand the focus back on its own.
         */
        suspend fun awaitActive(
            prevLinuxAppInfo: LinuxAppInfo,
            timeout: Duration = ACTIVATION_TIMEOUT,
        ): Boolean {
            val display = INSTANCE.XOpenDisplay(null) ?: return false
            return try {
                awaitActiveOn(display, prevLinuxAppInfo.window, timeout)
            } finally {
                INSTANCE.XCloseDisplay(display)
            }
        }

        private suspend fun awaitActiveOn(
            display: Display,
            window: Window,
            timeout: Duration,
        ): Boolean {
            val activated =
                pollUntil(timeout, ACTIVATION_POLL_INTERVAL) {
                    getActiveWindow(display) == window
                }
            if (!activated) {
                logger.warn {
                    "Window $window was not reported active within $timeout " +
                        "(current active: ${getActiveWindow(display)})"
                }
            }
            return activated
        }

        private suspend fun toPaste(
            display: Display,
            keyCodes: List<Int>,
        ) {
            val xTest = X11.XTest.INSTANCE
            val pressed = mutableListOf<Int>()
            try {
                for (keyCode in keyCodes) {
                    xTest.XTestFakeKeyEvent(display, keyCode, true, NativeLong(0))
                    pressed.add(keyCode)
                }
                // Xlib buffers requests: flush so the keys are held for the delay
                INSTANCE.XFlush(display)
                delay(KEY_HOLD)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.error(e) { "toPaste error" }
            } finally {
                // Release even when the coroutine is cancelled mid-hold: a key that was
                // already flushed must not stay pressed on the X server.
                for (keyCode in pressed.asReversed()) {
                    xTest.XTestFakeKeyEvent(display, keyCode, false, NativeLong(0))
                }
                INSTANCE.XFlush(display)
            }
        }

        suspend fun toPaste(keyCodes: List<Int>) {
            val display = INSTANCE.XOpenDisplay(null) ?: return
            try {
                toPaste(display, keyCodes)
            } finally {
                INSTANCE.XCloseDisplay(display)
            }
        }

        fun getRunningWindows(): List<LinuxAppInfo> {
            val display = INSTANCE.XOpenDisplay(null) ?: return emptyList()
            return try {
                val rootWindow = INSTANCE.XDefaultRootWindow(display)
                val sizeRef = NativeLongByReference()
                val prop =
                    WMCtrl.getProperty(
                        display,
                        rootWindow,
                        X11.XA_WINDOW,
                        "_NET_CLIENT_LIST",
                        sizeRef,
                    ) ?: return emptyList()
                try {
                    val byteSize = sizeRef.value.toLong()
                    val count = (byteSize / Native.LONG_SIZE).toInt()
                    if (count <= 0) return emptyList()

                    val windowIds =
                        when (Native.LONG_SIZE) {
                            java.lang.Long.BYTES -> {
                                prop.getLongArray(0, count).toList()
                            }
                            Integer.BYTES -> {
                                prop.getIntArray(0, count).map { it.toLong() }
                            }
                            else -> emptyList()
                        }

                    windowIds.mapNotNull { id ->
                        if (id == 0L) return@mapNotNull null
                        val window = Window(id)
                        WMCtrl.getWindowClass(display, window)?.let { (_, className) ->
                            LinuxAppInfo(window, className)
                        }
                    }
                } finally {
                    INSTANCE.XFree(prop)
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to get running windows" }
                emptyList()
            } finally {
                INSTANCE.XCloseDisplay(display)
            }
        }

        @Synchronized
        fun getWindow(windowTitle: String): Window? {
            val display = INSTANCE.XOpenDisplay(null) ?: return null
            return try {
                val rootWindow = INSTANCE.XDefaultRootWindow(display)
                WMCtrl.run { findWindowByTitle(display, rootWindow, windowTitle) }
            } finally {
                INSTANCE.XCloseDisplay(display)
            }
        }
    }
}
