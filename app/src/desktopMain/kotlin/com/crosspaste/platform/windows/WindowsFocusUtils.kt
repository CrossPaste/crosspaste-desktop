package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.Imm32
import com.crosspaste.platform.windows.api.Shell32
import com.crosspaste.platform.windows.api.User32
import com.crosspaste.platform.windows.api.windowProcessId
import com.sun.jna.Native
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.platform.win32.WinUser.SW_HIDE
import com.sun.jna.platform.win32.WinUser.SW_RESTORE
import com.sun.jna.ptr.IntByReference
import io.github.oshai.kotlinlogging.KotlinLogging

object WindowsFocusUtils {

    private val logger = KotlinLogging.logger {}

    private val user32 = User32.INSTANCE

    private val searchActivation = WindowsSearchWindowActivation(user32, Kernel32.INSTANCE)

    private val trayHitTest = WindowsTrayHitTest(user32, Shell32.INSTANCE, Kernel32.INSTANCE.GetCurrentProcessId())

    private val searchPointer = WindowsSearchWindowPointer(user32, trayHitTest::contains)

    private val inputMethod = WindowsInputMethod(user32, Imm32.INSTANCE)

    private val pasteKeystroke = WindowsPasteKeystroke(user32)

    fun isSearchWindowComposing(search: HWND?): Boolean = inputMethod.isComposing(search)

    fun isTrayForeground(): Boolean = user32.GetForegroundWindow()?.let(trayHitTest::ownsForeground) == true

    fun isTrayIcon(
        x: Int,
        y: Int,
    ): Boolean = trayHitTest.contains(x, y)

    fun isOutsideSearchWindow(
        x: Int,
        y: Int,
        search: HWND?,
        bubble: HWND?,
    ): Boolean = searchPointer.isOutside(x, y, search, bubble)

    fun activateSearchWindow(hwnd: HWND?): Boolean = searchActivation.activate(hwnd)

    private const val WS_EX_TOOLWINDOW = 0x00000080

    private const val GWLP_HWNDPARENT = -8

    private const val WS_EX_NOACTIVATE = 0x08000000

    fun isWindowForeground(hwnd: HWND?): Boolean = hwnd != null && user32.GetForegroundWindow() == hwnd

    fun isOtherApplicationForeground(): Boolean {
        val processId = getForegroundProcessId() ?: return false
        return processId != Kernel32.INSTANCE.GetCurrentProcessId()
    }

    /**
     * Whether the foreground window belongs to the same process as [hwnd]. Compared by
     * process rather than handle: the app may bring up another of its windows (an owner
     * frame, a dialog) on activation, and the keystroke still reaches it.
     */
    fun isForegroundProcessOf(hwnd: HWND): Boolean {
        val processId = user32.windowProcessId(hwnd) ?: return false
        return processId == getForegroundProcessId()
    }

    private fun getForegroundProcessId(): Int? = user32.GetForegroundWindow()?.let(user32::windowProcessId)

    @Synchronized
    fun bringToFront(
        prevThreadId: Int?,
        searchWindow: HWND?,
    ) {
        val handle = searchWindow ?: return

        val targetThreadId =
            if (prevThreadId != null) {
                user32.GetWindowThreadProcessId(handle, IntByReference())
            } else {
                0
            }

        // Attaching a thread to itself is invalid (ERROR_INVALID_PARAMETER).
        val shouldAttach = prevThreadId != null && prevThreadId != targetThreadId
        if (shouldAttach) {
            val success =
                user32.AttachThreadInput(
                    DWORD(prevThreadId.toLong()),
                    DWORD(targetThreadId.toLong()),
                    true,
                )
            if (!success) {
                logger.error { "AttachThreadInput failed: ${Kernel32.INSTANCE.GetLastError()}" }
                return
            }
        }

        try {
            user32.ShowWindow(handle, SW_RESTORE)
            user32.BringWindowToTop(handle)
            if (user32.SetForegroundWindow(handle)) {
                logger.info { "Foreground window set successfully" }
            } else {
                logger.info { "Failed to set foreground window. Please switch manually" }
            }
        } finally {
            if (shouldAttach) {
                user32.AttachThreadInput(
                    DWORD(prevThreadId.toLong()),
                    DWORD(targetThreadId.toLong()),
                    false,
                )
            }
        }
    }

    fun backToBack(
        backWindow: HWND?,
        previousHwnd: HWND?,
    ) {
        backWindow?.let { hwnd ->
            user32.ShowWindow(hwnd, SW_HIDE)
        }

        previousHwnd?.let { hwnd ->
            user32.ShowWindow(hwnd, WinUser.SW_SHOW)
            user32.SetForegroundWindow(hwnd)
        }
    }

    fun paste(keyCodes: List<Int>) = pasteKeystroke.send(keyCodes)

    /**
     * Keeps the window off the taskbar and stops a click on it from making it the
     * foreground window, so the app the user is pasting into keeps keyboard focus.
     */
    fun makeNonActivating(hwnd: HWND) {
        val style = user32.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE)
        user32.SetWindowLong(
            hwnd,
            WinUser.GWL_EXSTYLE,
            style or WS_EX_TOOLWINDOW or WS_EX_NOACTIVATE,
        )
    }

    /** Makes [owner] the owner of [hwnd]: Windows always stacks an owned window above its owner. */
    fun setOwner(
        hwnd: HWND,
        owner: HWND,
    ) {
        // The previous owner comes back, which is null both for an unowned window and on
        // failure: only the last error tells them apart.
        Native.setLastError(0)
        if (user32.SetWindowLongPtr(hwnd, GWLP_HWNDPARENT, owner.pointer) == null) {
            val error = Native.getLastError()
            if (error != 0) {
                logger.warn { "Failed to set the owner window: error $error" }
            }
        }
    }

    fun findPasteWindow(windowTitle: String): HWND? =
        user32.FindWindow(null, windowTitle)?.also { hwnd ->
            // Set the window icon not to be displayed on the taskbar
            val style =
                user32.GetWindowLong(
                    hwnd,
                    WinUser.GWL_EXSTYLE,
                )
            user32.SetWindowLong(
                hwnd,
                WinUser.GWL_EXSTYLE,
                style or WS_EX_TOOLWINDOW,
            )
        }
}
