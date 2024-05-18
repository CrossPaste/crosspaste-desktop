package com.clipevery.os.linux.api

import com.clipevery.app.DesktopAppWindowManager.mainWindowTitle
import com.clipevery.app.DesktopAppWindowManager.searchWindowTitle
import com.clipevery.app.LinuxAppInfo
import com.clipevery.os.linux.api.WMCtrl.getActiveWindow
import com.sun.jna.Native
import com.sun.jna.platform.unix.X11
import com.sun.jna.platform.unix.X11.Atom
import com.sun.jna.platform.unix.X11.Display
import com.sun.jna.platform.unix.X11.Window
import java.nio.file.Path
import javax.imageio.ImageIO

interface X11Api : X11 {

    fun XGetSelectionOwner(
        display: Display,
        selection: Atom,
    ): Long

    companion object {

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

        fun bringToBack(
            windowTitle: String,
            prevLinuxAppInfo: LinuxAppInfo?,
            toPaste: Boolean,
        ) {
            INSTANCE.XOpenDisplay(null)?.let { display ->
                prevLinuxAppInfo?.let {
                    WMCtrl.activeWindow(display, it.window)
                    if (toPaste) {
                        // todo
                        println("toPaste")
                    }
                }
                INSTANCE.XCloseDisplay(display)
            }
        }

        @Synchronized
        private fun getWindow(windowTitle: String): Window? {
            return if (windowTitle == mainWindowTitle) {
                findMainWindow()
            } else {
                findSearchWindow()
            }
        }

        private fun findMainWindow(): Window? {
            if (mainWindow == null) {
                INSTANCE.XOpenDisplay(null)?.let { display ->
                    val rootWindow = INSTANCE.XDefaultRootWindow(display)
                    WMCtrl.findWindowByTitle(display, rootWindow, mainWindowTitle)?.let {
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
                    WMCtrl.findWindowByTitle(display, rootWindow, searchWindowTitle)?.let {
                        searchWindow = it
                    }
                }
            }
            return searchWindow
        }
    }
}
