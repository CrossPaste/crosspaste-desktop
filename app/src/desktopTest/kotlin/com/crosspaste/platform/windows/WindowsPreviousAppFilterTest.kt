package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.User32
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.ptr.IntByReference
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsPreviousAppFilterTest {
    private val user32 = mockk<User32>(relaxed = true)
    private val ownWindow = HWND(Pointer(101))
    private val foreignWindow = HWND(Pointer(102))
    private val goneWindow = HWND(Pointer(103))
    private val taskbar = HWND(Pointer(104))
    private val filter = WindowsPreviousAppFilter(user32, 42)

    @BeforeEach
    fun setup() {
        every { user32.IsWindowVisible(any()) } returns true
        every { user32.GetWindowThreadProcessId(any(), any()) } answers {
            when (firstArg<HWND>()) {
                ownWindow -> 42
                goneWindow -> return@answers 0
                else -> 43
            }.let { secondArg<IntByReference>().value = it }
            1
        }
        every { user32.GetClassNameW(any(), any(), any()) } answers {
            val name = if (firstArg<HWND>() == taskbar) "Shell_TrayWnd" else "Notepad"
            name.toCharArray().copyInto(secondArg())
            name.length
        }
    }

    @Test
    fun `excludes every window of the current process`() {
        assertFalse(filter.accepts(ownWindow))
    }

    @Test
    fun `accepts a visible window of another process`() {
        assertTrue(filter.accepts(foreignWindow))
    }

    @Test
    fun `does not exclude a window whose process cannot be resolved`() {
        assertTrue(filter.accepts(goneWindow))
    }

    @Test
    fun `still excludes the taskbar and invisible windows`() {
        assertFalse(filter.accepts(taskbar))
        every { user32.IsWindowVisible(foreignWindow) } returns false
        assertFalse(filter.accepts(foreignWindow))
    }
}
