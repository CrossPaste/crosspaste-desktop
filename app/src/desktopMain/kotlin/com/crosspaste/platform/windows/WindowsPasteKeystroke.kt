package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.User32
import com.sun.jna.platform.win32.BaseTSD.ULONG_PTR
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.HKL
import com.sun.jna.platform.win32.WinDef.WORD
import com.sun.jna.platform.win32.WinUser.INPUT
import com.sun.jna.ptr.IntByReference
import io.github.oshai.kotlinlogging.KotlinLogging

internal class WindowsPasteKeystroke(
    private val user32: User32,
) {

    companion object {
        private const val KEYEVENTF_EXTENDEDKEY = 0x0001

        private const val KEYEVENTF_KEYUP = 0x0002

        private const val MAPVK_VK_TO_VSC = 0

        // Keys whose scan code carries the E0 prefix. Without the extended flag Windows
        // reads them as their numpad twin: Insert becomes Numpad 0 while NumLock is on.
        private val EXTENDED_KEYS =
            setOf(
                0x21, // VK_PRIOR
                0x22, // VK_NEXT
                0x23, // VK_END
                0x24, // VK_HOME
                0x25, // VK_LEFT
                0x26, // VK_UP
                0x27, // VK_RIGHT
                0x28, // VK_DOWN
                0x2C, // VK_SNAPSHOT
                0x2D, // VK_INSERT
                0x2E, // VK_DELETE
                0x5B, // VK_LWIN
                0x5C, // VK_RWIN
                0x5D, // VK_APPS
                0x6F, // VK_DIVIDE
                0x90, // VK_NUMLOCK
                0xA3, // VK_RCONTROL
                0xA5, // VK_RMENU
            )
    }

    private val logger = KotlinLogging.logger {}

    /**
     * Presses [keyCodes] in order and releases them in reverse, all in one SendInput
     * call so the user's own typing cannot interleave with the shortcut.
     */
    @Suppress("UNCHECKED_CAST")
    fun send(keyCodes: List<Int>) {
        if (keyCodes.isEmpty()) {
            return
        }
        val events = keyCodes.map { it to false } + keyCodes.reversed().map { it to true }
        val inputs = INPUT().toArray(events.size) as Array<INPUT>
        val layout = foregroundKeyboardLayout()
        events.forEachIndexed { index, (keyCode, keyUp) ->
            fill(inputs[index], keyCode, keyUp, layout)
        }
        val sent = user32.SendInput(DWORD(inputs.size.toLong()), inputs, inputs[0].size()).toInt()
        if (sent != inputs.size) {
            // UIPI blocks injection into apps running elevated, usually without any error
            logger.warn {
                "SendInput delivered $sent of ${inputs.size} key events, error ${Kernel32.INSTANCE.GetLastError()}"
            }
        }
    }

    private fun foregroundKeyboardLayout(): HKL? {
        val foreground = user32.GetForegroundWindow() ?: return null
        val threadId = user32.GetWindowThreadProcessId(foreground, IntByReference())
        // Zero means lookup failed; passing it to GetKeyboardLayout would query our own thread.
        if (threadId == 0) return null
        return user32.GetKeyboardLayout(threadId)
    }

    private fun fill(
        input: INPUT,
        keyCode: Int,
        keyUp: Boolean,
        layout: HKL?,
    ) {
        input.type = DWORD(INPUT.INPUT_KEYBOARD.toLong())
        // Because setting INPUT_INPUT_KEYBOARD is not enough:
        // https://groups.google.com/d/msg/jna-users/NDBGwC1VZbU/cjYCQ1CjBwAJ
        input.input.setType("ki")
        var flags = if (keyUp) KEYEVENTF_KEYUP else 0
        if (keyCode in EXTENDED_KEYS) {
            flags = flags or KEYEVENTF_EXTENDEDKEY
        }
        input.input.ki.wVk = WORD(keyCode.toLong())
        // Match the target's layout for scan-code consumers such as remote desktop clients.
        // If it cannot be resolved, keep VK input instead of guessing with our thread's layout.
        val scanCode = layout?.let { user32.MapVirtualKeyEx(keyCode, MAPVK_VK_TO_VSC, it) } ?: 0
        input.input.ki.wScan = WORD(scanCode.toLong())
        input.input.ki.dwFlags = DWORD(flags.toLong())
        input.input.ki.time = DWORD(0)
        input.input.ki.dwExtraInfo = ULONG_PTR(0)
    }
}
