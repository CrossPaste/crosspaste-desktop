package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.User32
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.BOOL
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinUser.GA_ROOTOWNER
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
    private val button = HWND(Pointer(102))
    private val primary = HMONITOR(Pointer(201))
    private val secondary = HMONITOR(Pointer(202))
    private val probe = WindowsFullscreenProbe(user32)

    // Secondary monitor sits left of the primary one, at negative coordinates
    private val monitorRects =
        mapOf(
            primary to rect(0, 0, 1920, 1080),
            secondary to rect(-2560, 0, 0, 1440),
        )

    private fun setup(
        window: RECT,
        className: String = "Chrome_WidgetWin_1",
        windowMonitor: HMONITOR = primary,
        buttonMonitor: HMONITOR = primary,
    ) {
        every { user32.GetForegroundWindow() } returns foreground
        every { user32.GetAncestor(foreground, GA_ROOTOWNER) } returns foreground
        every { user32.GetClassNameW(foreground, any(), any()) } answers {
            className.toCharArray().copyInto(secondArg())
            className.length
        }
        every { user32.GetWindowRect(foreground, any()) } answers {
            secondArg<RECT>().copyFrom(window)
            true
        }
        every { user32.MonitorFromWindow(foreground, any()) } returns windowMonitor
        every { user32.MonitorFromWindow(button, any()) } returns buttonMonitor
        every { user32.GetMonitorInfo(any<HMONITOR>(), any<MONITORINFO>()) } answers {
            secondArg<MONITORINFO>().rcMonitor.copyFrom(monitorRects.getValue(firstArg()))
            BOOL(true)
        }
    }

    @Test
    fun `window filling the button's monitor is full screen`() {
        setup(rect(0, 0, 1920, 1080))
        assertTrue(probe.isFullscreenOver(button))
    }

    @Test
    fun `full screen on a secondary monitor at negative coordinates`() {
        setup(rect(-2560, 0, 0, 1440), windowMonitor = secondary, buttonMonitor = secondary)
        assertTrue(probe.isFullscreenOver(button))
    }

    @Test
    fun `full screen on another monitor leaves the button alone`() {
        setup(rect(-2560, 0, 0, 1440), windowMonitor = secondary, buttonMonitor = primary)
        assertFalse(probe.isFullscreenOver(button))
    }

    @Test
    fun `popup owned by a full-screen window counts as full screen`() {
        val popup = HWND(Pointer(103))
        setup(rect(0, 0, 1920, 1080))
        mockWindow(popup, rect(1500, 900, 1900, 1060))
        every { user32.GetForegroundWindow() } returns popup
        every { user32.GetAncestor(popup, GA_ROOTOWNER) } returns foreground
        assertTrue(probe.isFullscreenOver(button))
    }

    @Test
    fun `full-screen window owned by an ordinary window counts as full screen`() {
        val owner = HWND(Pointer(104))
        setup(rect(0, 0, 1920, 1080))
        mockWindow(owner, rect(100, 100, 900, 700))
        every { user32.GetAncestor(foreground, GA_ROOTOWNER) } returns owner
        assertTrue(probe.isFullscreenOver(button))
    }

    @Test
    fun `maximized window overhanging the work area by its frame is not full screen`() {
        setup(rect(-8, -8, 1928, 1048))
        assertFalse(probe.isFullscreenOver(button))
    }

    @Test
    fun `desktop wallpaper window is not full screen`() {
        setup(rect(0, 0, 1920, 1080), className = "WorkerW")
        assertFalse(probe.isFullscreenOver(button))
    }

    @Test
    fun `no foreground window is not full screen`() {
        every { user32.GetForegroundWindow() } returns null
        assertFalse(probe.isFullscreenOver(button))
    }

    private fun mockWindow(
        hwnd: HWND,
        bounds: RECT,
    ) {
        every { user32.GetClassNameW(hwnd, any(), any()) } returns 0
        every { user32.GetWindowRect(hwnd, any()) } answers {
            secondArg<RECT>().copyFrom(bounds)
            true
        }
        every { user32.MonitorFromWindow(hwnd, any()) } returns primary
    }

    private fun RECT.copyFrom(other: RECT) {
        left = other.left
        top = other.top
        right = other.right
        bottom = other.bottom
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
}
