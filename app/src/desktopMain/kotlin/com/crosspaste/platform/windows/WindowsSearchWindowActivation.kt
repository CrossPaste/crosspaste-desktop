package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.User32
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser.SMTO_ABORTIFHUNG
import com.sun.jna.platform.win32.WinUser.SMTO_ERRORONEXIT
import com.sun.jna.platform.win32.WinUser.SW_RESTORE
import com.sun.jna.ptr.IntByReference
import io.github.oshai.kotlinlogging.KotlinLogging

/** Uses the live foreground input queue, rather than a cached previous app's thread. */
internal class WindowsSearchWindowActivation(
    private val user32: User32,
    private val kernel32: Kernel32,
) {
    private val logger = KotlinLogging.logger {}

    fun activate(hwnd: HWND?): Boolean {
        val window = hwnd ?: return false
        val windowThread = user32.GetWindowThreadProcessId(window, IntByReference())
        if (windowThread == 0) {
            logger.warn { "Search activation rejected: native window no longer exists" }
            return false
        }
        val foreground = user32.GetForegroundWindow()

        val callerThread = kernel32.GetCurrentThreadId()
        val foregroundThread =
            foreground?.let { user32.GetWindowThreadProcessId(it, IntByReference()) } ?: 0
        val attach = foreground != window && foregroundThread != 0 && foregroundThread != callerThread
        if (attach && !user32.AttachThreadInput(DWORD(callerThread.toLong()), DWORD(foregroundThread.toLong()), true)) {
            logger.error { "Search activation AttachThreadInput failed: ${kernel32.GetLastError()}" }
            return false
        }

        try {
            user32.ShowWindow(window, SW_RESTORE)
            user32.BringWindowToTop(window)
            if (!user32.SetForegroundWindow(window)) {
                logger.warn { "Search native activation request was rejected" }
                return false
            }
            // AWT's HWND belongs to its native toolkit thread, not this EDT.
            // Process its foreground notification before detaching the caller's input queue.
            // This returns as soon as WM_NULL is processed; the timeout only bounds a hung peer.
            val acknowledged =
                user32.SendMessageTimeout(
                    window,
                    User32.WM_NULL,
                    WPARAM(0),
                    LPARAM(0),
                    SMTO_ABORTIFHUNG or SMTO_ERRORONEXIT,
                    1000,
                    null,
                )
            if (acknowledged == null || acknowledged.toLong() == 0L) {
                logger.warn { "Search native activation notification was not acknowledged" }
                return false
            }
        } finally {
            if (attach) {
                user32.AttachThreadInput(DWORD(callerThread.toLong()), DWORD(foregroundThread.toLong()), false)
            }
        }
        return user32.GetForegroundWindow() == window
    }
}
