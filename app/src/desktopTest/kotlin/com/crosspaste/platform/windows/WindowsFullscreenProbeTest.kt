package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.Dwmapi
import com.crosspaste.platform.windows.api.User32
import com.crosspaste.platform.windows.api.WndEnumProc
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.BOOL
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.platform.win32.WinUser.HMONITOR
import com.sun.jna.platform.win32.WinUser.MONITORINFO
import com.sun.jna.ptr.IntByReference
import io.mockk.every
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsFullscreenProbeTest {
    private val user32 = mockk<User32>()
    private val dwmapi = mockk<Dwmapi>()
    private val button = HWND(Pointer(100))
    private val primary = HMONITOR(Pointer(201))
    private val secondary = HMONITOR(Pointer(202))
    private val probe = WindowsFullscreenProbe(user32, dwmapi, OWN_PID)

    private val primaryBounds = rect(0, 0, 1920, 1080)

    // Secondary monitor sits left of the primary one, at negative coordinates
    private val secondaryBounds = rect(-2560, 0, 0, 1440)

    private class FakeWindow(
        val bounds: RECT,
        val className: String = "Chrome_WidgetWin_1",
        val pid: Int = 7,
        val exStyle: Int = 0,
        val owner: HWND? = null,
        val visible: Boolean = true,
        val iconic: Boolean = false,
        val cloaked: Boolean = false,
    )

    // Front to back, as EnumWindows reports them
    private var stack: List<FakeWindow> = emptyList()

    private fun hwndOf(index: Int) = HWND(Pointer(1000L + index))

    private fun window(hwnd: HWND) = stack[(Pointer.nativeValue(hwnd.pointer) - 1000).toInt()]

    @BeforeTest
    fun setup() {
        every { user32.MonitorFromWindow(button, any()) } returns primary
        every { user32.GetMonitorInfo(any<HMONITOR>(), any<MONITORINFO>()) } answers {
            val bounds = if (firstArg<HMONITOR>() == primary) primaryBounds else secondaryBounds
            secondArg<MONITORINFO>().rcMonitor.copyFrom(bounds)
            BOOL(true)
        }
        every { user32.EnumWindows(any<WndEnumProc>(), null) } answers {
            val callback = firstArg<WndEnumProc>()
            stack.indices.all { callback.callback(hwndOf(it), null) }
        }
        every { user32.IsWindowVisible(any()) } answers { window(firstArg()).visible }
        every { user32.IsIconic(any()) } answers { window(firstArg()).iconic }
        every { user32.GetWindowThreadProcessId(any(), any()) } answers {
            secondArg<IntByReference>().value = window(firstArg()).pid
            1
        }
        every { user32.GetWindowLong(any(), WinUser.GWL_EXSTYLE) } answers { window(firstArg()).exStyle }
        every { user32.GetWindow(any(), any()) } answers { window(firstArg()).owner }
        every { user32.GetClassNameW(any(), any(), any()) } answers {
            val name = window(firstArg()).className
            name.toCharArray().copyInto(secondArg())
            name.length
        }
        every { user32.GetWindowRect(any(), any()) } answers {
            secondArg<RECT>().copyFrom(window(firstArg()).bounds)
            true
        }
        every { dwmapi.DwmGetWindowAttribute(any(), Dwmapi.DWMWA_CLOAKED, any(), any()) } answers {
            thirdArg<IntByReference>().value = if (window(firstArg()).cloaked) 1 else 0
            0
        }
    }

    @Test
    fun `full-screen window on the button's monitor hides it whichever app has the focus`() {
        stack = listOf(FakeWindow(primaryBounds))
        assertTrue(probe.isFullscreenOver(button))
    }

    @Test
    fun `full screen on a secondary monitor at negative coordinates`() {
        every { user32.MonitorFromWindow(button, any()) } returns secondary
        stack = listOf(FakeWindow(rect(100, 100, 900, 700)), FakeWindow(secondaryBounds))
        assertTrue(probe.isFullscreenOver(button))
    }

    @Test
    fun `full screen on another monitor leaves the button alone`() {
        stack = listOf(FakeWindow(secondaryBounds), FakeWindow(rect(100, 100, 900, 700)))
        assertFalse(probe.isFullscreenOver(button))
    }

    @Test
    fun `ordinary window in front of the full-screen one shows the button`() {
        stack = listOf(FakeWindow(rect(100, 100, 900, 700)), FakeWindow(primaryBounds))
        assertFalse(probe.isFullscreenOver(button))
    }

    @Test
    fun `overlays, owned popups and our own windows in front are looked past`() {
        stack =
            listOf(
                FakeWindow(rect(1880, 500, 1928, 548), pid = OWN_PID),
                FakeWindow(rect(0, 1032, 1920, 1080), className = "Shell_TrayWnd", exStyle = WS_EX_TOPMOST),
                FakeWindow(rect(1500, 900, 1900, 1060), owner = HWND(Pointer(1))),
                FakeWindow(rect(800, 400, 1100, 500), exStyle = WS_EX_TOOLWINDOW),
                FakeWindow(rect(100, 100, 900, 700), cloaked = true),
                FakeWindow(rect(100, 100, 900, 700), iconic = true),
                FakeWindow(primaryBounds),
            )
        assertTrue(probe.isFullscreenOver(button))
    }

    @Test
    fun `chromium full-screen window a pixel short of the monitor is full screen`() {
        stack = listOf(FakeWindow(rect(0, 0, 1920, 1079)))
        assertTrue(probe.isFullscreenOver(button))
    }

    @Test
    fun `maximized window with an auto-hidden taskbar overhangs the monitor and is not full screen`() {
        stack = listOf(FakeWindow(rect(-8, -8, 1928, 1088)))
        assertFalse(probe.isFullscreenOver(button))
    }

    @Test
    fun `maximized window overhanging the work area by its frame is not full screen`() {
        stack = listOf(FakeWindow(rect(-8, -8, 1928, 1048)))
        assertFalse(probe.isFullscreenOver(button))
    }

    @Test
    fun `desktop wallpaper window is not full screen`() {
        stack = listOf(FakeWindow(primaryBounds, className = "WorkerW"))
        assertFalse(probe.isFullscreenOver(button))
    }

    @Test
    fun `no windows is not full screen`() {
        stack = emptyList()
        assertFalse(probe.isFullscreenOver(button))
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

    companion object {
        private const val OWN_PID = 42
        private const val WS_EX_TOOLWINDOW = 0x00000080
        private const val WS_EX_TOPMOST = 0x00000008
    }
}
