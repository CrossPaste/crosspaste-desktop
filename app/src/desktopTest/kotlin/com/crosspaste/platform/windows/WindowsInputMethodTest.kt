package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.Imm32
import com.crosspaste.platform.windows.api.User32
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser.GUITHREADINFO
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@EnabledOnOs(OS.WINDOWS)
class WindowsInputMethodTest {
    private val user32 = mockk<User32>(relaxed = true)
    private val imm32 = mockk<Imm32>(relaxed = true)
    private val search = HWND(Pointer(101))
    private val focused = HWND(Pointer(202))
    private val context = Pointer(303)
    private val inputMethod = WindowsInputMethod(user32, imm32)

    init {
        every { user32.GetForegroundWindow() } returns search
        every { user32.GetWindowThreadProcessId(search, any()) } returns 77
        every { user32.GetGUIThreadInfo(77, any()) } answers {
            secondArg<GUITHREADINFO>().hwndFocus = focused
            true
        }
        every { imm32.ImmGetContext(focused) } returns context
        every { imm32.ImmGetCompositionStringW(context, Imm32.GCS_COMPSTR, null, 0) } returns 6
    }

    @Test
    fun `composition uses native window thread focus rather than hook thread or frame context`() {
        assertTrue(inputMethod.isComposing(search))
        verify(exactly = 0) { imm32.ImmGetContext(search) }
        verify(exactly = 1) { imm32.ImmReleaseContext(focused, context) }
    }

    @Test
    fun `committed composition and native errors do not exempt foreign clicks`() {
        for (bytes in listOf(0, -1, -2)) {
            every { imm32.ImmGetCompositionStringW(context, Imm32.GCS_COMPSTR, null, 0) } returns bytes
            assertFalse(inputMethod.isComposing(search))
        }
        verify(exactly = 3) { imm32.ImmReleaseContext(focused, context) }
    }

    @Test
    fun `search without foreground cannot exempt an outside click even with a composition`() {
        every { user32.GetForegroundWindow() } returns focused
        assertFalse(inputMethod.isComposing(search))
        assertFalse(inputMethod.isComposing(null))
        verify(exactly = 0) { imm32.ImmGetContext(any()) }
    }

    @Test
    fun `failed thread lookup never queries foreground thread implicitly`() {
        every { user32.GetWindowThreadProcessId(search, any()) } returns 0
        assertFalse(inputMethod.isComposing(search))
        verify(exactly = 0) { user32.GetGUIThreadInfo(any(), any()) }
    }

    @Test
    fun `missing focus or context does not invent a composition`() {
        every { user32.GetGUIThreadInfo(77, any()) } returns false
        assertFalse(inputMethod.isComposing(search))
        every { user32.GetGUIThreadInfo(77, any()) } returns true
        assertFalse(inputMethod.isComposing(search))
        every { user32.GetGUIThreadInfo(77, any()) } answers {
            secondArg<GUITHREADINFO>().hwndFocus = focused
            true
        }
        every { imm32.ImmGetContext(focused) } returns null
        assertFalse(inputMethod.isComposing(search))
        verify(exactly = 0) { imm32.ImmGetCompositionStringW(any(), any(), any(), any()) }
    }

    @Test
    fun `context is released if querying the composition throws`() {
        every { imm32.ImmGetCompositionStringW(context, Imm32.GCS_COMPSTR, null, 0) } throws
            IllegalStateException("query")
        assertFailsWith<IllegalStateException> { inputMethod.isComposing(search) }
        verify(exactly = 1) { imm32.ImmReleaseContext(focused, context) }
    }
}
