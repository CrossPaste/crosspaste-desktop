package com.crosspaste.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchWindowFocusSessionTest {
    @Test
    fun `brief startup gain and loss must not cancel activation or close the window`() =
        runTest {
            val session = SearchWindowFocusSession()
            var activations = 0
            launch {
                session.activate {
                    delay(100)
                    activations++
                    true
                }
            }
            runCurrent()
            session.onForegroundChanged(true) { error("Gaining focus") }
            session.onForegroundChanged(false) { error("Opening jitter must not close") }
            session.deferFocusLoss({ it() }, { true }) { error("Opening jitter must not close") }
            advanceUntilIdle()
            assertEquals(1, activations)
            var dismissed = false
            session.onForegroundChanged(false) {
                session.deferFocusLoss({ it() }, { true }) { dismissed = true }
            }
            assertTrue(dismissed)
        }

    @Test
    fun `synthetic AWT gain does not replace confirmed activation`() =
        runTest {
            val session = SearchWindowFocusSession()
            session.onFocusGained(false)
            session.activate { false }
            session.onForegroundChanged(false) { error("Never acquired foreground") }
            session.deferFocusLoss({ it() }, { true }) { error("Never acquired foreground") }
        }

    @Test
    fun `native acknowledgement establishes acquisition without an AWT gain event`() =
        runTest {
            val session = SearchWindowFocusSession()
            session.activate { true }
            var dismissed = false
            session.deferFocusLoss({ it() }, { true }) { dismissed = true }
            assertTrue(dismissed)
        }

    @Test
    fun `outside press closes an unfocused window and cancels pending activation`() =
        runTest {
            val session = SearchWindowFocusSession()
            launch {
                session.activate {
                    delay(100)
                    error("Must not steal focus after an outside click")
                }
            }
            runCurrent()
            var dismissed = false
            session.deferOutsidePress({ it() }, { true }) { dismissed = true }
            advanceUntilIdle()
            assertTrue(dismissed)
        }

    @Test
    fun `outside press still closes after activation was denied and no focus event can be emitted`() =
        runTest {
            val session = SearchWindowFocusSession()
            session.activate { false }
            var dismissed = false
            session.deferOutsidePress({ it() }, { true }) { dismissed = true }
            assertTrue(dismissed)
        }

    @Test
    fun `search content and owned bubble presses preserve the window`() =
        runTest {
            val session = SearchWindowFocusSession()
            session.activate { true }
            val queued = mutableListOf<() -> Unit>()
            session.deferOutsidePress(queued::add, { false }) { error("Inside interaction") }
            queued.single().invoke()
            var dismissed = false
            session.deferOutsidePress({ it() }, { true }) { dismissed = true }
            assertTrue(dismissed)
        }

    @Test
    fun `focus regain invalidates queued loss even if focus is lost again`() =
        runTest {
            val session = SearchWindowFocusSession()
            session.activate { true }
            val queued = mutableListOf<() -> Unit>()
            var dismissals = 0
            session.deferFocusLoss(queued::add, { true }) { dismissals++ }
            session.onFocusGained(true)
            session.deferFocusLoss(queued::add, { true }) { dismissals++ }
            queued[0]()
            assertEquals(0, dismissals)
            queued[1]()
            assertEquals(1, dismissals)
        }

    @Test
    fun `native departure while AWT still has focus cannot dismiss as startup churn`() =
        runTest {
            val session = SearchWindowFocusSession()
            session.activate { true }
            var awtFocused = true
            var dismissed = false
            val queued = mutableListOf<() -> Unit>()
            session.onForegroundChanged(false) {
                session.deferFocusLoss(queued::add, { !awtFocused }) { dismissed = true }
            }
            queued.removeAt(0).invoke()
            assertFalse(dismissed)
            awtFocused = false
            session.deferFocusLoss({ it() }, { !awtFocused }) { dismissed = true }
            assertTrue(dismissed)
        }

    @Test
    fun `late native departure can close after ambiguous AWT loss`() =
        runTest {
            val session = SearchWindowFocusSession()
            session.activate { true }
            var otherForeground = false
            var dismissed = false
            session.deferFocusLoss({ it() }, { otherForeground }) { dismissed = true }
            assertFalse(dismissed)
            otherForeground = true
            session.onForegroundChanged(false) {
                session.deferFocusLoss({ it() }, { otherForeground }) { dismissed = true }
            }
            assertTrue(dismissed)
        }

    @Test
    fun `closed appearance discards queued mouse and focus callbacks and cannot activate`() =
        runTest {
            val session = SearchWindowFocusSession()
            session.activate { true }
            val queued = mutableListOf<() -> Unit>()
            session.deferOutsidePress(queued::add, { true }) { error("Stale mouse press") }
            session.deferFocusLoss(queued::add, { true }) { error("Stale focus loss") }
            session.close()
            queued.forEach { it() }
            session.activate { error("Closed appearance") }
        }
}
