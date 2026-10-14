package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.User32
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser.GA_ROOT
import com.sun.jna.platform.win32.WinUser.GW_OWNER
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@EnabledOnOs(OS.WINDOWS)
class WindowsSearchWindowPointerTest {
    private val user32 = mockk<User32>(relaxed = true)
    private val search = HWND(Pointer(101))
    private val bubble = HWND(Pointer(202))
    private val child = HWND(Pointer(303))
    private val pointer = WindowsSearchWindowPointer(user32)

    @Test
    fun `external press uses native coordinates including negative monitor coordinates`() {
        every { user32.WindowFromPoint(match { it.x == -100 && it.y == 250 }) } returns child
        every { user32.GetAncestor(child, GA_ROOT) } returns child
        every { user32.GetWindow(child, DWORD(GW_OWNER.toLong())) } returns null
        assertTrue(pointer.isOutside(-100, 250, search, bubble))
    }

    @Test
    fun `search child press is inside`() {
        every { user32.WindowFromPoint(any()) } returns child
        every { user32.GetAncestor(child, GA_ROOT) } returns search
        assertFalse(pointer.isOutside(100, 200, search, bubble))
    }

    @Test
    fun `editor bubble press is inside`() {
        every { user32.WindowFromPoint(any()) } returns child
        every { user32.GetAncestor(child, GA_ROOT) } returns bubble
        assertFalse(pointer.isOutside(100, 200, search, bubble))
    }

    @Test
    fun `popup owned by search or bubble is inside even outside their rectangles`() {
        every { user32.WindowFromPoint(any()) } returns child
        every { user32.GetAncestor(child, GA_ROOT) } returns child
        every { user32.GetWindow(child, DWORD(GW_OWNER.toLong())) } returns search
        assertFalse(pointer.isOutside(500, 600, search, bubble))
        every { user32.GetWindow(child, DWORD(GW_OWNER.toLong())) } returns bubble
        assertFalse(pointer.isOutside(500, 600, search, bubble))
    }

    @Test
    fun `missing window or unresolved hit cannot trigger dismissal`() {
        assertFalse(pointer.isOutside(100, 200, null, bubble))
        verify(exactly = 0) { user32.WindowFromPoint(any()) }
        every { user32.WindowFromPoint(any()) } returns null
        assertFalse(pointer.isOutside(100, 200, search, bubble))
    }
}
