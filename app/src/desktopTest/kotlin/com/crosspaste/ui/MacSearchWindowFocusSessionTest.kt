package com.crosspaste.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacSearchWindowFocusSessionTest {
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
}
