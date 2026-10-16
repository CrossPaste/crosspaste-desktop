package com.crosspaste.platform.macos

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class MacStatusItemClickTrackerTest {
    @Test
    fun `hover without mouse-down creates no pending click`() {
        assertNull(MacStatusItemClickTracker().pendingClick)
    }

    @Test
    fun `click stays pending until the asynchronous toggle completes`() {
        val clicks = MacStatusItemClickTracker()
        clicks.onInput(isStatusItemClick = true)
        val click = assertNotNull(clicks.pendingClick)
        // Mouse-up does not cancel the token: the native tray callback can
        // still be queued in the library after the physical button is released.
        assertSame(click, clicks.pendingClick)
        clicks.complete(click)
        assertNull(clicks.pendingClick)
    }

    @Test
    fun `keyboard or outside mouse-down cancels an abandoned click`() {
        val clicks = MacStatusItemClickTracker()
        clicks.onInput(isStatusItemClick = true)
        clicks.onInput(isStatusItemClick = false)
        assertNull(clicks.pendingClick)
    }

    @Test
    fun `an old toggle completion cannot clear a newer click`() {
        val clicks = MacStatusItemClickTracker()
        clicks.onInput(isStatusItemClick = true)
        val first = assertNotNull(clicks.pendingClick)
        clicks.onInput(isStatusItemClick = true)
        val second = assertNotNull(clicks.pendingClick)
        assertNotSame(first, second)
        clicks.complete(first)
        assertSame(second, clicks.pendingClick)
        clicks.complete(second)
        assertNull(clicks.pendingClick)
    }

    @Test
    fun `a callback without a token cannot cancel a later native click`() {
        val clicks = MacStatusItemClickTracker()
        val beforeMouseDown = clicks.pendingClick
        clicks.onInput(isStatusItemClick = true)
        val click = assertNotNull(clicks.pendingClick)
        clicks.complete(beforeMouseDown)
        assertSame(click, clicks.pendingClick)
    }
}
