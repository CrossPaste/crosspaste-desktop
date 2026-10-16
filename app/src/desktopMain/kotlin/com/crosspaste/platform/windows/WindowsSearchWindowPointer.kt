package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.User32
import com.crosspaste.platform.windows.api.windowFromPoint
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser.GA_ROOT
import com.sun.jna.platform.win32.WinUser.GW_OWNER
import com.sun.jna.ptr.IntByReference
import io.github.oshai.kotlinlogging.KotlinLogging

/** Hit-test native pixels, including owned menus and the editor bubble on any monitor. */
internal class WindowsSearchWindowPointer(
    private val user32: User32,
    private val isTrayIcon: (Int, Int) -> Boolean,
) {
    private val logger = KotlinLogging.logger {}

    /** Capture before dispatch: an IME candidate window may disappear when the click commits it. */
    fun describePoint(
        x: Int,
        y: Int,
    ): String {
        val hit = user32.windowFromPoint(x, y)
        return "hit=${describeWindow(hit)}, foreground=${describeWindow(user32.GetForegroundWindow())}"
    }

    fun isOutside(
        x: Int,
        y: Int,
        search: HWND?,
        bubble: HWND?,
    ): Boolean {
        fun report(
            outside: Boolean,
            reason: String,
            hit: HWND? = null,
            owners: Collection<HWND> = emptyList(),
        ): Boolean {
            logger.info {
                "Windows search pointer result: x=$x, y=$y, outside=$outside, reason=$reason, " +
                    "search=$search, bubble=$bubble, hit=${describeWindow(hit)}, " +
                    "ownerChain=${owners.joinToString(transform = ::describeWindow)}"
            }
            return outside
        }

        if (search == null) return report(false, "search-missing")
        if (isTrayIcon(x, y)) return report(false, "tray-icon")
        val hit = user32.windowFromPoint(x, y) ?: return report(false, "hit-unresolved")
        var window: HWND? = user32.GetAncestor(hit, GA_ROOT) ?: return report(false, "root-unresolved", hit)
        val visited = mutableSetOf<HWND>()
        while (window != null && visited.add(window)) {
            if (window == search || window == bubble) return report(false, "search-or-bubble-owner", hit, visited)
            window = user32.GetWindow(window, DWORD(GW_OWNER.toLong()))
        }
        return report(true, "foreign-window", hit, visited)
    }

    private fun describeWindow(window: HWND?): String {
        if (window == null) return "null"
        val name = CharArray(256)
        val classLength = user32.GetClassNameW(window, name, name.size)
        val processId = IntByReference()
        val threadId = user32.GetWindowThreadProcessId(window, processId)
        return "$window(class=${Native.toString(name)}, classLength=$classLength, " +
            "pid=${processId.value}, tid=$threadId)"
    }
}
