package com.crosspaste.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacSearchWindowFocusSessionTest {
    @Test
    fun `tray focus loss leaves a delayed tray toggle on the hide branch`() {
        val session = MacSearchWindowFocusSession(gainedFocus = true)
        val queued = mutableListOf<() -> Unit>()
        val focus = focusState(appActive = true)
        var shown = true
        session.deferFocusLoss(queued::add, { focus.shouldDismiss }) { shown = false }

        // The AWT loss callback runs before the tray library delivers its click.
        queued.single()()
        assertTrue(shown)
        shown = !shown
        assertFalse(shown, "The tray click must hide the existing appearance instead of reopening it")
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
    ): MacSearchWindowFocusState =
        MacSearchWindowFocusState(
            appActive = appActive,
            searchFocused = searchFocused,
            bubbleFocused = bubbleFocused,
            mainFocused = mainFocused,
        )
}
