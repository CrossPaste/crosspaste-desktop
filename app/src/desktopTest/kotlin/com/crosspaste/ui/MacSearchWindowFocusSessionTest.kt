package com.crosspaste.ui

import androidx.compose.ui.window.WindowState
import com.crosspaste.app.WindowInfo
import com.crosspaste.app.WindowTrigger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacSearchWindowFocusSessionTest {
    @Test
    fun `tray click preserves the window when the focus recheck arrives before toggle`() {
        for (appActive in listOf(true, false)) {
            val session = MacSearchWindowFocusSession(gainedFocus = true)
            val queued = mutableListOf<() -> Unit>()
            var shown = true
            session.deferFocusLoss(
                queued::add,
                { focusState(appActive = appActive, pointerOnStatusItem = true).shouldDismiss },
            ) {
                error("Only the tray toggle may hide the window during a tray click")
            }

            // macOS 27 can deactivate the app before delivering the tray callback.
            queued.single()()
            assertTrue(shown)
            shown = !shown
            assertFalse(shown, "The delayed tray toggle must hide instead of reopening")
        }
    }

    @Test
    fun `tray toggle arriving first leaves queued focus loss unable to close a new appearance`() {
        val session = MacSearchWindowFocusSession(gainedFocus = true)
        val queued = mutableListOf<() -> Unit>()
        val appearance = WindowInfo(true, WindowState(), WindowTrigger.TRAY_ICON)
        var current = appearance
        session.deferFocusLoss(
            enqueue = queued::add,
            shouldDismiss = { focusState(appActive = false).shouldDismiss },
            isCurrentAppearance = { current === appearance },
            dismiss = { error("An old tray departure must not close a reopened window") },
        )

        // The tray callback hides first; a shortcut then opens a new appearance.
        current = current.copy(show = !current.show)
        assertFalse(current.show)
        current = current.copy(show = true, trigger = WindowTrigger.SHORTCUT)
        // Run before Compose has disposed the original session.
        queued.single()()
        assertTrue(current.show)

        val reopened = MacSearchWindowFocusSession(gainedFocus = true)
        reopened.deferFocusLoss({ it() }, { focusState(appActive = false).shouldDismiss }) {
            current = current.copy(show = false)
        }
        assertFalse(current.show, "A real outside click must still dismiss the new appearance")
    }

    @Test
    fun `stale queued losses are rejected after focus recheck then tray toggle and reopen`() {
        val session = MacSearchWindowFocusSession(gainedFocus = true)
        val queued = mutableListOf<() -> Unit>()
        val appearance = WindowInfo(true, WindowState(), WindowTrigger.TRAY_ICON)
        var current = appearance
        var pointerOnStatusItem = true
        repeat(2) {
            session.deferFocusLoss(
                enqueue = queued::add,
                shouldDismiss = {
                    focusState(appActive = false, pointerOnStatusItem = pointerOnStatusItem).shouldDismiss
                },
                isCurrentAppearance = { current === appearance },
                dismiss = { error("The old appearance must leave dismissal to the tray toggle") },
            )
        }
        queued[0]()
        current = current.copy(show = !current.show)
        pointerOnStatusItem = false
        assertFalse(current.show)
        // Even an identical show/trigger pair is a different window appearance.
        current = current.copy(show = true)
        queued[1]()
        assertTrue(current.show)
    }

    @Test
    fun `focus loss away from the status item still dismisses`() {
        val session = MacSearchWindowFocusSession(gainedFocus = true)
        val queued = mutableListOf<() -> Unit>()
        var dismissed = false
        // Hovering the icon without a pressed button (Cmd+Tab) is not a tray click.
        session.deferFocusLoss(
            queued::add,
            { focusState(appActive = false, pointerOnStatusItem = false).shouldDismiss },
        ) { dismissed = true }
        queued.single()()
        assertTrue(dismissed)
    }

    @Test
    fun `app deactivation after a retained key-window loss still dismisses`() {
        val session = MacSearchWindowFocusSession(gainedFocus = true)
        val queued = mutableListOf<() -> Unit>()
        var focus = focusState(appActive = true)
        var dismissed = false
        session.deferFocusLoss(queued::add, { focus.shouldDismiss }) { dismissed = true }
        queued.removeAt(0)()
        assertFalse(dismissed)

        // Deactivation can arrive without a second windowLostFocus event.
        focus = focusState(appActive = false)
        session.deferFocusLoss(queued::add, { focus.shouldDismiss }) { dismissed = true }
        queued.removeAt(0)()
        assertTrue(dismissed)
    }

    @Test
    fun `switching apps immediately after focus gain dismisses without a timer`() {
        val session = MacSearchWindowFocusSession()
        val queued = mutableListOf<() -> Unit>()
        var dismissed = false
        session.onFocusGained()
        session.deferFocusLoss(queued::add, { focusState(appActive = false).shouldDismiss }) { dismissed = true }
        queued.single()()
        assertTrue(dismissed)
    }

    @Test
    fun `focus in the main window dismisses while the app stays active`() {
        assertTrue(focusState(appActive = true, mainFocused = true).shouldDismiss)
    }

    @Test
    fun `main window focus after a retained tray loss still dismisses`() {
        val session = MacSearchWindowFocusSession(gainedFocus = true)
        var focus = focusState(appActive = true)
        var dismissed = false
        session.deferFocusLoss({ it() }, { focus.shouldDismiss }) { dismissed = true }
        assertFalse(dismissed)
        focus = focusState(appActive = true, mainFocused = true)
        session.deferFocusLoss({ it() }, { focus.shouldDismiss }) { dismissed = true }
        assertTrue(dismissed)
    }

    @Test
    fun `search popup or edit bubble focus prevents dismissal`() {
        for (appActive in listOf(true, false)) {
            assertFalse(focusState(appActive = appActive, searchFocused = true).shouldDismiss)
            assertFalse(focusState(appActive = appActive, bubbleFocused = true).shouldDismiss)
        }
    }

    @Test
    fun `regaining app activation before the queued departure preserves the window`() {
        val session = MacSearchWindowFocusSession(gainedFocus = true)
        val queued = mutableListOf<() -> Unit>()
        var focus = focusState(appActive = false)
        var dismissed = false
        session.deferFocusLoss(queued::add, { focus.shouldDismiss }) { dismissed = true }
        focus = focusState(appActive = true)
        queued.single()()
        assertFalse(dismissed)
    }

    @Test
    fun `startup loss before any gain cannot close the window`() {
        val session = MacSearchWindowFocusSession()
        session.deferFocusLoss({ error("Startup loss must not enqueue dismissal") }, { true }) { error("Startup loss") }
    }

    @Test
    fun `outside loss immediately after first gain closes without a grace timer`() {
        val session = MacSearchWindowFocusSession()
        session.onFocusGained()
        val queued = mutableListOf<() -> Unit>()
        var dismissed = false
        session.deferFocusLoss(queued::add, { true }) { dismissed = true }
        assertFalse(dismissed)
        queued.single()()
        assertTrue(dismissed)
    }

    @Test
    fun `focus regain invalidates an earlier loss even when another loss follows`() {
        val session = MacSearchWindowFocusSession()
        session.onFocusGained()
        val queued = mutableListOf<() -> Unit>()
        var dismissals = 0
        session.deferFocusLoss(queued::add, { true }) { dismissals++ }
        session.onFocusGained()
        session.deferFocusLoss(queued::add, { true }) { dismissals++ }
        queued[0]()
        assertEquals(0, dismissals)
        queued[1]()
        assertEquals(1, dismissals)
    }

    @Test
    fun `final focus in search bubble or popup preserves the appearance`() {
        val session = MacSearchWindowFocusSession()
        session.onFocusGained()
        val queued = mutableListOf<() -> Unit>()
        var outside = true
        var dismissed = false
        session.deferFocusLoss(queued::add, { outside }) { dismissed = true }
        outside = false
        queued.single()()
        assertFalse(dismissed)
        outside = true
        session.deferFocusLoss({ it() }, { outside }) { dismissed = true }
        assertTrue(dismissed)
    }

    @Test
    fun `hiding and reopening discards callbacks from the old appearance`() {
        val previous = MacSearchWindowFocusSession(gainedFocus = true)
        val queued = mutableListOf<() -> Unit>()
        previous.deferFocusLoss(queued::add, { true }) { error("Old appearance must not close reopened window") }
        previous.close()
        val current = MacSearchWindowFocusSession(gainedFocus = true)
        queued.single()()
        var dismissed = false
        current.deferFocusLoss({ it() }, { true }) { dismissed = true }
        assertTrue(dismissed)
    }

    @Test
    fun `already focused appearance needs no new gain event`() {
        val session = MacSearchWindowFocusSession(gainedFocus = true)
        var dismissed = false
        session.deferFocusLoss({ it() }, { true }) { dismissed = true }
        assertTrue(dismissed)
    }

    @Test
    fun `one departure closes only once and late gain cannot revive the appearance`() {
        val session = MacSearchWindowFocusSession(gainedFocus = true)
        val queued = mutableListOf<() -> Unit>()
        var dismissals = 0
        repeat(2) { session.deferFocusLoss(queued::add, { true }) { dismissals++ } }
        queued.forEach { it() }
        session.onFocusGained()
        session.deferFocusLoss({ it() }, { true }) { dismissals++ }
        assertEquals(1, dismissals)
    }

    private fun focusState(
        appActive: Boolean,
        searchFocused: Boolean = false,
        bubbleFocused: Boolean = false,
        mainFocused: Boolean = false,
        pointerOnStatusItem: Boolean = false,
    ): MacSearchWindowFocusState =
        MacSearchWindowFocusState(
            appActive = appActive,
            searchFocused = searchFocused,
            bubbleFocused = bubbleFocused,
            mainFocused = mainFocused,
            pointerOnStatusItem = pointerOnStatusItem,
        )
}
