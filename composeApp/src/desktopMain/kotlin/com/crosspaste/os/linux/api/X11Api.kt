package com.crosspaste.os.linux.api

import com.crosspaste.app.DesktopAppWindowManager.Companion.MAIN_WINDOW_TITLE
import com.crosspaste.app.DesktopAppWindowManager.Companion.SEARCH_WINDOW_TITLE
import com.crosspaste.app.LinuxAppInfo
import com.crosspaste.os.linux.api.WMCtrl.getActiveWindow
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.platform.unix.X11
import com.sun.jna.platform.unix.X11.Display
import com.sun.jna.platform.unix.X11.Window
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import java.nio.file.Path
import javax.imageio.ImageIO

interface X11Api : X11 {

    companion object {

        private val logger = KotlinLogging.logger {}

        val INSTANCE: X11Api = Native.load("X11", X11Api::class.java)

        private var mainWindow: Window? = null

        private var searchWindow: Window? = null

        fun getActiveWindow(): LinuxAppInfo? {
            INSTANCE.XOpenDisplay(null)?.let { display ->
                getActiveWindow(display)?.let { previousWindow ->
                    WMCtrl.getWindowClass(display, previousWindow)?.let {
                        return@getActiveWindow LinuxAppInfo(previousWindow, it.second)
                    }
                }
                INSTANCE.XCloseDisplay(display)
            }
            return null
        }

        fun saveAppIcon(
            window: Window,
            iconPath: Path,
        ) {
            INSTANCE.XOpenDisplay(null)?.let { display ->
                WMCtrl.getPropertyAsIcon(display, window)?.let { buffer ->
                    ImageIO.write(buffer, "png", iconPath.toFile())
                }
                INSTANCE.XCloseDisplay(display)
            }
        }

        fun bringToFront(windowTitle: String): LinuxAppInfo? {
            return INSTANCE.XOpenDisplay(null)?.let { display ->
                val linuxAppInfo: LinuxAppInfo? =
                    getActiveWindow(display)?.let { previousWindow ->
                        WMCtrl.getWindowClass(display, previousWindow)?.let {
                            LinuxAppInfo(previousWindow, it.second)
                        }
                    }

                getWindow(windowTitle)?.let { window ->
                    WMCtrl.activeWindow(display, window)
                }

                INSTANCE.XCloseDisplay(display)
                return linuxAppInfo
            }
        }

        fun bringToBack(prevLinuxAppInfo: LinuxAppInfo?) {
            INSTANCE.XOpenDisplay(null)?.let { display ->
                prevLinuxAppInfo?.let {
                    WMCtrl.activeWindow(display, it.window)
                }
                INSTANCE.XCloseDisplay(display)
            }
        }

        suspend fun bringToBack(
            prevLinuxAppInfo: LinuxAppInfo?,
            toPaste: Boolean,
            keyCodes: List<Int>,
        ) {
            INSTANCE.XOpenDisplay(null)?.let { display ->
                prevLinuxAppInfo?.let {
                    WMCtrl.activeWindow(display, it.window)
                    if (toPaste) {
                        toPaste(display, keyCodes)
                    }
                }
                INSTANCE.XCloseDisplay(display)
            }
        }

        private suspend fun toPaste(
            display: Display,
            keyCodes: List<Int>,
        ) {
            val xTest = X11.XTest.INSTANCE
            try {
                for (keyCode in keyCodes) {
                    xTest.XTestFakeKeyEvent(display, keyCode, true, NativeLong(0))
                }
                delay(100)

                for (keyCode in keyCodes.reversed()) {
                    xTest.XTestFakeKeyEvent(display, keyCode, false, NativeLong(0))
                }
            } catch (e: Exception) {
                logger.error(e) { "toPaste error" }
            }
        }

        suspend fun toPaste(keyCodes: List<Int>) {
            INSTANCE.XOpenDisplay(null)?.let { display ->
                toPaste(display, keyCodes)
                INSTANCE.XCloseDisplay(display)
            }
        }

        @Synchronized
        private fun getWindow(windowTitle: String): Window? {
            return if (windowTitle == MAIN_WINDOW_TITLE) {
                findMainWindow()
            } else {
                findSearchWindow()
            }
        }

        private fun findMainWindow(): Window? {
            if (mainWindow == null) {
                INSTANCE.XOpenDisplay(null)?.let { display ->
                    val rootWindow = INSTANCE.XDefaultRootWindow(display)
                    WMCtrl.findWindowByTitle(display, rootWindow, MAIN_WINDOW_TITLE)?.let {
                        mainWindow = it
                    }
                }
            }
            return mainWindow
        }

        private fun findSearchWindow(): Window? {
            if (searchWindow == null) {
                INSTANCE.XOpenDisplay(null)?.let { display ->
                    val rootWindow = INSTANCE.XDefaultRootWindow(display)
                    WMCtrl.findWindowByTitle(display, rootWindow, SEARCH_WINDOW_TITLE)?.let {
                        searchWindow = it
                    }
                }
            }
            return searchWindow
        }
    }
}
