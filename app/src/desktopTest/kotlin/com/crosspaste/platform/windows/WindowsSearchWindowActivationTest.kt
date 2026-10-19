package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.User32
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinUser.SW_RESTORE
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@EnabledOnOs(OS.WINDOWS)
class WindowsSearchWindowActivationTest {
    private val user32 = mockk<User32>(relaxed = true)
    private val kernel32 = mockk<Kernel32>(relaxed = true)
    private val window = HWND(Pointer(101))
    private val foreground = HWND(Pointer(202))
    private val activation = WindowsSearchWindowActivation(user32, kernel32)

    init {
        every { user32.GetWindowThreadProcessId(window, any()) } returns 11
        every { user32.GetWindowThreadProcessId(foreground, any()) } returns 22
        every { kernel32.GetCurrentThreadId() } returns 33
        every { user32.GetForegroundWindow() } returnsMany listOf(foreground, window)
        every { user32.AttachThreadInput(any(), any(), any()) } returns true
        every { user32.SetForegroundWindow(window) } returns true
        every { user32.SendMessageTimeout(window, 0, any(), any(), any(), any(), null) } returns LRESULT(1)
    }

    @Test
    fun `activation attaches actual caller to live foreground instead of window owner thread`() {
        assertTrue(activation.activate(window))
        verifyOrder {
            user32.AttachThreadInput(match { it.toInt() == 33 }, match { it.toInt() == 22 }, true)
            user32.ShowWindow(window, SW_RESTORE)
            user32.BringWindowToTop(window)
            user32.SetForegroundWindow(window)
            user32.SendMessageTimeout(window, 0, any(), any(), any(), any(), null)
            user32.AttachThreadInput(match { it.toInt() == 33 }, match { it.toInt() == 22 }, false)
        }
        verify(exactly = 0) {
            user32.AttachThreadInput(any(), match { it.toInt() == 11 }, any())
        }
    }

    @Test
    fun `caller already owning foreground must not attach to itself`() {
        every { kernel32.GetCurrentThreadId() } returns 22
        assertTrue(activation.activate(window))
        verify(exactly = 0) { user32.AttachThreadInput(any(), any(), any()) }
        verify { user32.SetForegroundWindow(window) }
    }

    @Test
    fun `no foreground needs no input queue attachment`() {
        every { user32.GetForegroundWindow() } returnsMany listOf(null, window)
        assertTrue(activation.activate(window))
        verify(exactly = 0) { user32.AttachThreadInput(any(), any(), any()) }
    }

    @Test
    fun `already foreground still acknowledges native opening but does not attach`() {
        every { user32.GetForegroundWindow() } returns window
        assertTrue(activation.activate(window))
        verify(exactly = 0) { user32.AttachThreadInput(any(), any(), any()) }
        verify { user32.SetForegroundWindow(window) }
    }

    @Test
    fun `attachment failure stops activation and reports failure`() {
        every { user32.AttachThreadInput(any(), any(), true) } returns false
        every { kernel32.GetLastError() } returns 87
        assertFalse(activation.activate(window))
        verify(exactly = 0) { user32.SetForegroundWindow(any()) }
        verify(exactly = 0) { user32.AttachThreadInput(any(), any(), false) }
    }

    @Test
    fun `native activation failure still detaches input queues`() {
        every { user32.SetForegroundWindow(window) } returns false
        assertFalse(activation.activate(window))
        verify { user32.AttachThreadInput(any(), any(), false) }
    }

    @Test
    fun `native exception still detaches input queues`() {
        every { user32.ShowWindow(window, SW_RESTORE) } throws IllegalStateException("Native failure")
        assertFailsWith<IllegalStateException> { activation.activate(window) }
        verify { user32.AttachThreadInput(any(), any(), false) }
    }

    @Test
    fun `destroyed window must not be activated`() {
        every { user32.GetWindowThreadProcessId(window, any()) } returns 0
        assertFalse(activation.activate(window))
        verify(exactly = 0) { user32.AttachThreadInput(any(), any(), any()) }
        verify(exactly = 0) { user32.SetForegroundWindow(any()) }
    }

    @Test
    fun `accepted request without actual foreground acquisition must report failure`() {
        every { user32.GetForegroundWindow() } returns foreground
        assertFalse(activation.activate(window))
        verify { user32.AttachThreadInput(any(), any(), false) }
    }

    @Test
    fun `native queue acknowledgement failure cannot be reported as success`() {
        every { user32.SendMessageTimeout(window, 0, any(), any(), any(), any(), null) } returns LRESULT(0)
        assertFalse(activation.activate(window))
        verify { user32.AttachThreadInput(any(), any(), false) }
    }
}
