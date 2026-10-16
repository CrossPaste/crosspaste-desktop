package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.User32
import com.crosspaste.ui.SearchWindowFocusSession
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser.GA_ROOT
import com.sun.jna.platform.win32.WinUser.GW_OWNER
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
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
    private var trayHit = false
    private val pointer = WindowsSearchWindowPointer(user32) { _, _ -> trayHit }

    @Test
    fun `own tray press is left to tray toggle instead of hiding before its callback`() {
        trayHit = true
        assertFalse(pointer.isOutside(100, 200, search, bubble))
        verify(exactly = 0) { user32.WindowFromPoint(any()) }
    }

    @Test
    fun `tray toggle closes once whether queued outside check runs before or after callback`() =
        runTest {
            trayHit = true
            for (outsideFirst in listOf(true, false)) {
                val session = SearchWindowFocusSession()
                session.activate { true }
                var shown = true
                var outsideDismissals = 0
                val queued = mutableListOf<() -> Unit>()
                session.deferOutsidePress(queued::add, { pointer.isOutside(100, 200, search, bubble) }) {
                    outsideDismissals++
                    shown = false
                }
                if (outsideFirst) queued.single().invoke()
                shown = !shown
                if (!outsideFirst) queued.single().invoke()
                assertFalse(shown)
                kotlin.test.assertEquals(0, outsideDismissals)
            }
        }

    @Test
    fun `external press uses native coordinates including negative monitor coordinates`() {
        every { user32.WindowFromPoint(0x0000_00fa_ffff_ff9cL) } returns child
        every { user32.GetAncestor(child, GA_ROOT) } returns child
        every { user32.GetWindow(child, DWORD(GW_OWNER.toLong())) } returns null
        assertTrue(pointer.isOutside(-100, 250, search, bubble))
    }

    @Test
    fun `monitor above and left preserves both signed coordinate bit patterns`() {
        every { user32.WindowFromPoint(0xffff_ffe7_ffff_ff9cUL.toLong()) } returns child
        every { user32.GetAncestor(child, GA_ROOT) } returns child
        every { user32.GetWindow(child, DWORD(GW_OWNER.toLong())) } returns null
        assertTrue(pointer.isOutside(-100, -25, search, bubble))
    }

    @Test
    fun `candidate that commits before queued hit test leaves the press inside search`() =
        runTest {
            val session = SearchWindowFocusSession()
            session.activate { true }
            every { user32.GetForegroundWindow() } returns search
            every { user32.WindowFromPoint(any()) } returns child
            every { user32.GetAncestor(child, GA_ROOT) } returns child
            every { user32.GetWindow(child, DWORD(GW_OWNER.toLong())) } returns null

            val queued = mutableListOf<() -> Unit>()
            session.deferOutsidePress(queued::add, { pointer.isOutside(100, 200, search, bubble) }) {
                error("Committed candidate selection must not dismiss search")
            }
            // The composition may already be empty when the mouse callback runs.
            every { user32.WindowFromPoint(any()) } returns search
            every { user32.GetAncestor(search, GA_ROOT) } returns search
            queued.single().invoke()

            // Suppressing those clicks must leave the session able to close on departure.
            every { user32.GetForegroundWindow() } returns child
            var dismissed = false
            session.onForegroundChanged(false) {
                session.deferFocusLoss({ it() }, { true }) { dismissed = true }
            }
            assertTrue(dismissed)
        }

    @Test
    fun `non activating foreign press dismisses even while search retains foreground`() =
        runTest {
            val session = SearchWindowFocusSession()
            session.activate { true }
            every { user32.GetForegroundWindow() } returns search
            every { user32.WindowFromPoint(any()) } returns child
            every { user32.GetAncestor(child, GA_ROOT) } returns child
            every { user32.GetWindow(child, DWORD(GW_OWNER.toLong())) } returns null
            var dismissed = false
            session.deferOutsidePress({ it() }, { pointer.isOutside(100, 200, search, bubble) }) { dismissed = true }
            assertTrue(dismissed)
        }

    @Test
    fun `outside check after foreground transfer closes search`() =
        runTest {
            val session = SearchWindowFocusSession()
            session.activate { true }
            every { user32.GetForegroundWindow() } returns child
            every { user32.WindowFromPoint(any()) } returns child
            every { user32.GetAncestor(child, GA_ROOT) } returns child
            every { user32.GetWindow(child, DWORD(GW_OWNER.toLong())) } returns null
            var dismissed = false
            session.deferOutsidePress({ it() }, { pointer.isOutside(100, 200, search, bubble) }) { dismissed = true }
            assertTrue(dismissed)
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
