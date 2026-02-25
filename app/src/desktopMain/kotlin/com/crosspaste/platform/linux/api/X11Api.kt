package com.crosspaste.platform.linux.api

import com.crosspaste.app.LinuxAppInfo
import com.crosspaste.platform.linux.api.WMCtrl.getActiveWindow
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.platform.unix.X11
import com.sun.jna.platform.unix.X11.Display
import com.sun.jna.platform.unix.X11.Window
import com.sun.jna.ptr.NativeLongByReference
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import java.nio.file.Path
import javax.imageio.ImageIO

interface X11Api : X11 {

    companion object {

        private val logger = KotlinLogging.logger {}

        val INSTANCE: X11Api = Native.load("X11", X11Api::class.java)

        fun getActiveWindow(): LinuxAppInfo? {
            val display = INSTANCE.XOpenDisplay(null) ?: return null
            return try {
                getActiveWindow(display)?.let { previousWindow ->
                    WMCtrl.getWindowClass(display, previousWindow)?.let {
                        LinuxAppInfo(previousWindow, it.second)
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
            source: NativeLong = NativeLong(1),
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

        suspend fun bringToBack(
            prevLinuxAppInfo: LinuxAppInfo?,
            keyCodes: List<Int>,
            source: NativeLong = NativeLong(1),
            xServerTime: NativeLong? = null,
        ) {
            val display = INSTANCE.XOpenDisplay(null) ?: return
            try {
                prevLinuxAppInfo?.let {
                    WMCtrl.activeWindow(display, it.window, source, xServerTime ?: NativeLong(X11.CurrentTime.toLong()))
                    toPaste(display, keyCodes)
                }
            } finally {
                INSTANCE.XCloseDisplay(display)
            }
        }

        private suspend fun toPaste(
            display: Display,
            keyCodes: List<Int>,
        ) {
            val xTest = X11.XTest.INSTANCE
            runCatching {
                for (keyCode in keyCodes) {
                    xTest.XTestFakeKeyEvent(display, keyCode, true, NativeLong(0))
                }
                delay(100)

                for (keyCode in keyCodes.reversed()) {
                    xTest.XTestFakeKeyEvent(display, keyCode, false, NativeLong(0))
                }
            }.onFailure { e ->
                logger.error(e) { "toPaste error" }
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
