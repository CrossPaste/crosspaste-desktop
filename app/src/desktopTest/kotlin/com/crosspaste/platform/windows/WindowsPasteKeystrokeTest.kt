package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.User32
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinUser.INPUT
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowsPasteKeystrokeTest {

    private val user32 = mockk<User32>(relaxed = true)

    private val sent = mutableListOf<Triple<Int, Int, Int>>()

    init {
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
    }
}
