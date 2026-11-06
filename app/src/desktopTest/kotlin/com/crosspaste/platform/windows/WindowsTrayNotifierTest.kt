package com.crosspaste.platform.windows

import com.crosspaste.notification.MessageType
import com.crosspaste.platform.windows.api.Shell32
import com.crosspaste.platform.windows.api.User32
import com.crosspaste.platform.windows.api.WndEnumProc
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.ptr.IntByReference
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsTrayNotifierTest {
    private val user32 = mockk<User32>(relaxed = true)
    private val shell32 = mockk<Shell32>()
    private val ownTray = HWND(Pointer(101))
    private val notifier = WindowsTrayNotifier(user32, shell32, 42)

    private fun withOwnTray() {
        every { user32.EnumWindows(any<WndEnumProc>(), null) } answers {
            firstArg<WndEnumProc>().callback(ownTray, null)
            true
        }
        every { user32.GetWindowThreadProcessId(ownTray, any()) } answers {
            secondArg<IntByReference>().value = 42
            1
        }
        every { user32.GetClassNameW(ownTray, any(), any()) } answers {
            "TRAY".toCharArray().copyInto(secondArg())
            4
        }
    }

    @Test
    fun `balloon modifies own tray icon with a silent typed info message`() {
        withOwnTray()
        var sent: Shell32.NotifyIconData? = null
        every { shell32.Shell_NotifyIcon(any(), any()) } answers {
            sent = secondArg()
            true
        }

        assertTrue(notifier.notify("Copy failed", "details", MessageType.Error))

        verify { shell32.Shell_NotifyIcon(WindowsTrayNotifier.NIM_MODIFY, any()) }
        val data = sent!!
        assertEquals(ownTray, data.hWnd)
        assertEquals(TRAY_ICON_ID, data.uID)
        assertEquals(WindowsTrayNotifier.NIF_INFO, data.uFlags)
        assertEquals("Copy failed", Native.toString(data.szInfoTitle))
        assertEquals("details", Native.toString(data.szInfo))
        assertEquals(WindowsTrayNotifier.NIIF_ERROR or WindowsTrayNotifier.NIIF_NOSOUND, data.dwInfoFlags)
    }

    @Test
    fun `long text is truncated to keep the terminating NUL`() {
        withOwnTray()
        var sent: Shell32.NotifyIconData? = null
        every { shell32.Shell_NotifyIcon(any(), any()) } answers {
            sent = secondArg()
            true
        }

        notifier.notify("t".repeat(100), "m".repeat(300), MessageType.Info)

        assertEquals(63, Native.toString(sent!!.szInfoTitle).length)
        assertEquals(255, Native.toString(sent!!.szInfo).length)
    }

    @Test
    fun `truncation never splits a surrogate pair`() {
        val buffer = CharArray(4)

        WindowsTrayNotifier.fill(buffer, "ab\uD83D\uDE00")

        assertEquals("ab", Native.toString(buffer))
    }

    @Test
    fun `missing tray icon reports failure without calling the shell`() {
        every { user32.EnumWindows(any<WndEnumProc>(), null) } returns true

        assertFalse(notifier.notify("title", "body", MessageType.Info))
        verify(exactly = 0) { shell32.Shell_NotifyIcon(any(), any()) }
    }
}
