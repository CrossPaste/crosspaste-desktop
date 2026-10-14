package com.crosspaste.ui

import com.crosspaste.platform.windows.api.User32
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.awt.EventQueue
import java.awt.Frame
import java.awt.Window
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@EnabledOnOs(OS.WINDOWS)
class WindowsSearchWindowPeerTest {
    @Test
    fun `utility type creates a focusable tool window before first visibility`() {
        EventQueue.invokeAndWait {
            val window = Frame()
            try {
                window.type = Window.Type.UTILITY
                window.isAutoRequestFocus = false
                window.isUndecorated = true
                window.addNotify()
                val hwnd = HWND(Native.getWindowPointer(window))
                val style = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE)
                val acknowledged =
                    User32.INSTANCE.SendMessageTimeout(
                        hwnd,
                        User32.WM_NULL,
                        com.sun.jna.platform.win32.WinDef
                            .WPARAM(0),
                        com.sun.jna.platform.win32.WinDef
                            .LPARAM(0),
                        WinUser.SMTO_ABORTIFHUNG or WinUser.SMTO_ERRORONEXIT,
                        1000,
                        null,
                    )
                assertTrue(acknowledged != null && acknowledged.toLong() != 0L, "Native peer must acknowledge WM_NULL")
                assertFalse(window.isVisible)
                assertTrue(window.focusableWindowState)
                assertTrue(style and 0x00000080 != 0, "WS_EX_TOOLWINDOW must exist before first show")
                assertTrue(style and 0x00040000 == 0, "WS_EX_APPWINDOW must not force a taskbar button")
                assertTrue(style and 0x08000000 == 0, "Interactive search must not have WS_EX_NOACTIVATE")
            } finally {
                window.dispose()
            }
        }
    }
}
