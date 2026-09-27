package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.User32
import com.crosspaste.ui.base.NativeMenuEntry
import com.sun.jna.Function
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.POINT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

/**
 * Win32 context menu drawn by the system, for windows that cannot use AWT's PopupMenu.
 *
 * AWT owner-draws its heavyweight menus through the logical-font tables of `fontconfig`,
 * and since the default encoding became UTF-8 (JDK 18) the Windows tables carry no
 * Chinese ranges at all: every CJK label renders as boxes, whatever font the menu is
 * given, because `PlatformFont` maps any physical family back to the logical one. A
 * plain Win32 menu is rendered by Windows with the system UI font and its font linking,
 * so every language displays.
 *
 * `TrackPopupMenu` runs a modal loop on the calling thread and needs an owner window that
 * this thread created and that is in the foreground, so the menu has a thread of its own
 * with a hidden owner and a message loop, like the clipboard listeners.
 */
object WindowsPopupMenu {

    private val logger = KotlinLogging.logger {}

    private val user32 = User32.INSTANCE
    private val getMessageW = Function.getFunction("user32", "GetMessageW", Function.ALT_CONVENTION)

    private const val WM_SHOW_MENU = User32.WM_USER + 1

    private const val TRACK_FLAGS =
        User32.TPM_LEFTALIGN or
            User32.TPM_TOPALIGN or
            User32.TPM_RIGHTBUTTON or
            User32.TPM_RETURNCMD or
            User32.TPM_NONOTIFY

    private class Request(
        val entries: List<NativeMenuEntry>,
        val dispatch: (() -> Unit) -> Unit,
    )

    private val pendingRequest = AtomicReference<Request?>()

    @Volatile
    private var isTracking = false

    // Held in a field so the JNA callback stub outlives the window that uses it
    private val wndProc =
        User32.WNDPROC { hWnd, uMsg, wParam, lParam ->
            handleMessage(hWnd, uMsg, wParam, lParam)
        }

    private val owner: HWND by lazy { startMenuThread() }

    /**
     * Opens the menu at the mouse cursor and returns at once. The chosen item's action is
     * handed to [dispatch] from the menu thread, so the caller picks where it runs.
     */
    fun show(
        entries: List<NativeMenuEntry>,
        dispatch: (() -> Unit) -> Unit,
    ) {
        pendingRequest.set(Request(entries, dispatch))
        user32.PostMessage(owner, WM_SHOW_MENU, WPARAM(0), LPARAM(0))
    }

    private fun startMenuThread(): HWND {
        val created = CompletableFuture<HWND>()
        Thread({ runMessageLoop(created) }, "WindowsPopupMenu").apply { isDaemon = true }.start()
        return created.get()
    }

    private fun runMessageLoop(created: CompletableFuture<HWND>) {
        val hwnd = user32.CreateWindowEx(0, "STATIC", "CrossPastePopupMenu", 0, 0, 0, 0, 0, null, 0, 0, null)
        if (hwnd == null) {
            created.completeExceptionally(
                IllegalStateException("CreateWindowEx failed: ${Kernel32.INSTANCE.GetLastError()}"),
            )
            return
        }
        user32.SetWindowLongPtr(hwnd, User32.GWL_WNDPROC, wndProc)
        created.complete(hwnd)

        val msg = WinUser.MSG()
        while (getMessageW.invokeInt(arrayOf(msg, null, 0, 0)) > 0) {
            user32.TranslateMessage(msg)
            user32.DispatchMessage(msg)
        }
    }

    private fun handleMessage(
        hWnd: HWND?,
        uMsg: Int,
        wParam: WPARAM?,
        lParam: LPARAM?,
    ): Int {
        if (uMsg == WM_SHOW_MENU && hWnd != null) {
            if (isTracking) {
                user32.EndMenu()
                user32.PostMessage(hWnd, WM_SHOW_MENU, WPARAM(0), LPARAM(0))
                return 0
            }
            pendingRequest.getAndSet(null)?.let { request ->
                isTracking = true
                try {
                    track(hWnd, request)
                } finally {
                    isTracking = false
                }
            }
            return 0
        }
        return user32.DefWindowProc(hWnd, uMsg, wParam, lParam).toInt()
    }

    private fun track(
        hwnd: HWND,
        request: Request,
    ) {
        val menu = user32.CreatePopupMenu()
        if (menu == null) {
            logger.error { "CreatePopupMenu failed: ${Kernel32.INSTANCE.GetLastError()}" }
            return
        }
        val actions = ArrayList<() -> Unit>()
        val prevForeground = user32.GetForegroundWindow()
        val picked =
            try {
                request.entries.forEach { entry ->
                    when (entry) {
                        is NativeMenuEntry.Item -> {
                            actions += entry.action
                            user32.AppendMenu(menu, User32.MF_STRING, actions.size, entry.label)
                        }
                        NativeMenuEntry.Separator -> user32.AppendMenu(menu, User32.MF_SEPARATOR, 0, null)
                    }
                }
                val cursor = POINT().also { user32.GetCursorPos(it) }
                // Unless the owner is the foreground window a click outside the menu does
                // not close it, and the WM_NULL afterwards is the matching part of that
                // long-standing Windows recipe.
                user32.SetForegroundWindow(hwnd)
                user32.TrackPopupMenu(menu, TRACK_FLAGS, cursor.x, cursor.y, 0, hwnd, null).also {
                    user32.PostMessage(hwnd, User32.WM_NULL, WPARAM(0), LPARAM(0))
                }
            } finally {
                user32.DestroyMenu(menu)
            }
        if (picked == 0 &&
            pendingRequest.get() == null &&
            prevForeground != null &&
            user32.GetForegroundWindow() == hwnd
        ) {
            user32.SetForegroundWindow(prevForeground)
        }
        actions.getOrNull(picked - 1)?.let(request.dispatch)
    }
}
