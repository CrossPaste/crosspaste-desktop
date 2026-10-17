package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.Shell32
import com.crosspaste.platform.windows.api.User32
import com.crosspaste.platform.windows.api.WndEnumProc
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.platform.win32.WinUser.GA_ROOT
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@EnabledOnOs(OS.WINDOWS)
class WindowsTrayHitTestTest {
    private val user32 = mockk<User32>(relaxed = true)
    private val shell32 = mockk<Shell32>()
    private val ownTray = HWND(Pointer(101))
    private val foreignTray = HWND(Pointer(102))
    private val taskbar = HWND(Pointer(201))
    private val overflow = HWND(Pointer(202))
    private val hitTest = WindowsTrayHitTest(user32, shell32, 42)

    @BeforeEach
    fun setup() {
        every { user32.EnumWindows(any<WndEnumProc>(), null) } answers {
            val callback = firstArg<WndEnumProc>()
            if (callback.callback(foreignTray, null)) callback.callback(ownTray, null)
            true
        }
        every { user32.GetWindowThreadProcessId(any(), any()) } answers {
            secondArg<com.sun.jna.ptr.IntByReference>().value =
                when (firstArg<HWND>()) {
                    ownTray -> 42
                    foreignTray -> 43
                    else -> 99
                }
            1
        }
        every { user32.GetClassNameW(any(), any(), any()) } answers {
            "TRAY".toCharArray().copyInto(secondArg())
            4
        }
        every { shell32.Shell_NotifyIconGetRect(any(), any()) } answers {
            val identifier = firstArg<Shell32.NotifyIconIdentifier>()
            assertEquals(ownTray, identifier.hWnd)
            assertEquals(1000, identifier.uID)
            assertEquals(if (com.sun.jna.Native.POINTER_SIZE == 8) 40 else 28, identifier.cbSize)
            secondArg<com.sun.jna.platform.win32.WinDef.RECT>().apply {
                left = -100
                top = 200
                right = -80
                bottom = 220
            }
            HRESULT(0)
        }
        every { user32.FindWindow("Shell_TrayWnd", null) } returns taskbar
        every { user32.WindowFromPoint(any()) } returns overflow
        every { user32.GetAncestor(overflow, GA_ROOT) } returns overflow
    }

    @Test
    fun `selects own process icon and uses native negative coordinates with exclusive right and bottom edges`() {
        assertTrue(hitTest.contains(-100, 200))
        assertTrue(hitTest.contains(-81, 219))
        assertFalse(hitTest.contains(-80, 210))
        assertFalse(hitTest.contains(-90, 220))
        assertFalse(hitTest.contains(-101, 210))
        verify(exactly = 0) { user32.GetClassNameW(foreignTray, any(), any()) }
    }

    @Test
    fun `rectangle is refreshed when icon moves`() {
        assertTrue(hitTest.contains(-90, 210))
        every { shell32.Shell_NotifyIconGetRect(any(), any()) } answers {
            secondArg<com.sun.jna.platform.win32.WinDef.RECT>().apply {
                left = 300
                top = 400
                right = 320
                bottom = 420
            }
            HRESULT(0)
        }
        assertFalse(hitTest.contains(-90, 210))
        assertTrue(hitTest.contains(310, 410))
    }

    @Test
    fun `missing own tray or failed shell query does not exclude unrelated clicks`() {
        every { shell32.Shell_NotifyIconGetRect(any(), any()) } returns HRESULT(-1)
        assertFalse(hitTest.contains(-90, 210))
        every { user32.EnumWindows(any<WndEnumProc>(), null) } returns true
        assertFalse(hitTest.contains(-90, 210))
    }

    @Test
    fun `wrong window class in own process is not a tray`() {
        every { user32.GetClassNameW(ownTray, any(), any()) } answers {
            "OTHER".toCharArray().copyInto(secondArg())
            5
        }
        assertFalse(hitTest.contains(-90, 210))
        verify(exactly = 0) { shell32.Shell_NotifyIconGetRect(any(), any()) }
    }

    @Test
    fun `foreground shell overflow containing own icon is left to mouse and tray actions`() {
        assertTrue(hitTest.ownsForeground(overflow))
        assertFalse(hitTest.ownsForeground(taskbar))
        verify(exactly = 2) { user32.WindowFromPoint(0x0000_00d2_ffff_ffa6L) }
    }

    @Test
    fun `window beneath an unavailable overflow panel is not mistaken for shell interaction`() {
        every { user32.GetWindowThreadProcessId(overflow, any()) } answers {
            secondArg<com.sun.jna.ptr.IntByReference>().value = 123
            1
        }
        assertFalse(hitTest.ownsForeground(overflow))
    }
}
