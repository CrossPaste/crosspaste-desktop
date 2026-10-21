package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.User32
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.HKL
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser.INPUT
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowsPasteKeystrokeTest {

    private val user32 = mockk<User32>(relaxed = true)

    private val foreground = HWND(Pointer(101))
    private val usLayout = HKL(0x04090409)
    private val dvorakLayout = HKL(0xF0020409.toInt())

    private val sent = mutableListOf<Triple<Int, Int, Int>>()

    init {
        every { user32.GetForegroundWindow() } returns foreground
        every { user32.GetWindowThreadProcessId(foreground, any()) } returns 77
        every { user32.GetKeyboardLayout(0) } returns usLayout
        every { user32.GetKeyboardLayout(77) } returns usLayout
        every { user32.MapVirtualKeyEx(any(), 0, any()) } answers { firstArg<Int>() + 0x100 }
        every { user32.SendInput(any(), any(), any()) } answers {
            secondArg<Array<INPUT>>().forEach { input ->
                val ki = input.input.ki
                sent += Triple(ki.wVk.toInt(), ki.wScan.toInt(), ki.dwFlags.toInt())
            }
            firstArg<DWORD>()
        }
    }

    @Test
    fun `presses in order and releases in reverse in a single call`() {
        WindowsPasteKeystroke(user32).send(listOf(0x11, 0x56)) // Ctrl+V

        verify(exactly = 1) { user32.SendInput(any(), any(), any()) }
        assertEquals(
            listOf(
                Triple(0x11, 0x111, 0),
                Triple(0x56, 0x156, 0),
                Triple(0x56, 0x156, 0x2),
                Triple(0x11, 0x111, 0x2),
            ),
            sent,
        )
    }

    @Test
    fun `marks Insert as an extended key so it is not read as Numpad 0`() {
        WindowsPasteKeystroke(user32).send(listOf(0x10, 0x2D)) // Shift+Insert

        assertEquals(listOf(0, 0x1, 0x3, 0x2), sent.map { it.third })
    }

    @Test
    fun `sends nothing without keys`() {
        WindowsPasteKeystroke(user32).send(emptyList())

        verify(exactly = 0) { user32.SendInput(any(), any(), any()) }
        verify(exactly = 0) { user32.GetForegroundWindow() }
    }

    @Test
    fun `uses the foreground layout for both down and up when caller layout differs`() {
        every { user32.GetKeyboardLayout(77) } returns dvorakLayout
        every { user32.MapVirtualKeyEx(0x11, 0, any()) } returns 0x1D
        every { user32.MapVirtualKeyEx(0x56, 0, usLayout) } returns 0x2F
        every { user32.MapVirtualKeyEx(0x56, 0, dvorakLayout) } returns 0x34

        WindowsPasteKeystroke(user32).send(listOf(0x11, 0x56))

        assertEquals(listOf(0x1D, 0x34, 0x34, 0x1D), sent.map { it.second })
        verify(exactly = 1) { user32.GetKeyboardLayout(77) }
        verify(exactly = 0) { user32.GetKeyboardLayout(0) }
        verify(exactly = 1) { user32.SendInput(any(), any(), any()) }
    }

    @Test
    fun `refreshes the target layout for each paste`() {
        val otherForeground = HWND(Pointer(202))
        every { user32.GetForegroundWindow() } returnsMany listOf(foreground, otherForeground)
        every { user32.GetWindowThreadProcessId(otherForeground, any()) } returns 88
        every { user32.GetKeyboardLayout(88) } returns dvorakLayout
        every { user32.MapVirtualKeyEx(0x56, 0, usLayout) } returns 0x2F
        every { user32.MapVirtualKeyEx(0x56, 0, dvorakLayout) } returns 0x34
        val keystroke = WindowsPasteKeystroke(user32)

        keystroke.send(listOf(0x56))
        keystroke.send(listOf(0x56))

        assertEquals(listOf(0x2F, 0x2F, 0x34, 0x34), sent.map { it.second })
    }

    @Test
    fun `missing foreground keeps virtual keys without guessing a scan code`() {
        every { user32.GetForegroundWindow() } returns null

        assertVirtualKeyFallback()

        verify(exactly = 0) { user32.GetWindowThreadProcessId(any(), any()) }
        verify(exactly = 0) { user32.GetKeyboardLayout(any()) }
    }

    @Test
    fun `failed target thread lookup never falls back to current thread layout`() {
        every { user32.GetWindowThreadProcessId(foreground, any()) } returns 0

        assertVirtualKeyFallback()

        verify(exactly = 0) { user32.GetKeyboardLayout(any()) }
    }

    @Test
    fun `missing target layout keeps virtual keys without guessing a scan code`() {
        every { user32.GetKeyboardLayout(77) } returns null

        assertVirtualKeyFallback()

        verify(exactly = 0) { user32.GetKeyboardLayout(0) }
    }

    private fun assertVirtualKeyFallback() {
        WindowsPasteKeystroke(user32).send(listOf(0x10, 0x2D))

        assertEquals(
            listOf(Triple(0x10, 0, 0), Triple(0x2D, 0, 1), Triple(0x2D, 0, 3), Triple(0x10, 0, 2)),
            sent,
        )
        verify(exactly = 0) { user32.MapVirtualKeyEx(any(), any(), any()) }
        verify(exactly = 1) { user32.SendInput(any(), any(), any()) }
    }
}
