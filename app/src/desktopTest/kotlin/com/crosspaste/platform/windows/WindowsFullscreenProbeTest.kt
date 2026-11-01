package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.User32
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.BOOL
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinUser.HMONITOR
import com.sun.jna.platform.win32.WinUser.MONITORINFO
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsFullscreenProbeTest {
    private val user32 = mockk<User32>()
    private val foreground = HWND(Pointer(101))
    private val probe = WindowsFullscreenProbe(user32)

    private fun setup(
        className: String,
        window: RECT,
    ) {
        every { user32.GetForegroundWindow() } returns foreground
        every { user32.GetClassNameW(foreground, any(), any()) } answers {
            className.toCharArray().copyInto(secondArg())
            className.length
        }
        every { user32.GetWindowRect(foreground, any()) } answers {
            secondArg<RECT>().apply {
                left = window.left
                top = window.top
                right = window.right
                bottom = window.bottom
            }
            true
        }
        every { user32.MonitorFromWindow(foreground, any()) } returns HMONITOR(Pointer(201))
        every { user32.GetMonitorInfo(any<HMONITOR>(), any<MONITORINFO>()) } answers {
            secondArg<MONITORINFO>().rcMonitor.apply {
                left = 0
                top = 0
                right = 1920
                bottom = 1080
            }
            BOOL(true)
        }
    }

    private fun rect(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) = RECT().apply {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
    }

    @Test
    fun `window filling the monitor is full screen`() {
        setup("Chrome_WidgetWin_1", rect(0, 0, 1920, 1080))
        assertTrue(probe.isForegroundFullscreen())
    }

    @Test
    fun `maximized window overhanging the monitor by its frame is not full screen`() {
        setup("Chrome_WidgetWin_1", rect(-8, -8, 1928, 1088))
        assertFalse(probe.isForegroundFullscreen())
    }

    @Test
    fun `desktop wallpaper window is not full screen`() {
        setup("WorkerW", rect(0, 0, 1920, 1080))
        assertFalse(probe.isForegroundFullscreen())
    }
}
