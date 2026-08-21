package com.crosspaste.cli.commands.pick

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SixelFetchStateTest {

    @Test
    fun resizeWhileInFlightLaunchesForTheNewBoxAndDropsTheOldReply() {
        val state = SixelFetchState()
        val oldBox = assertNotNull(state.nextRequest(7, 60, 9))
        // Same paste, new box after a resize: an id-only guard would swallow
        // this and the image would never reappear — it must launch anew
        val newBox = assertNotNull(state.nextRequest(7, 40, 9))
        assertNotEquals(oldBox, newBox)
        // The old-box reply racing back is stale and must not clear the
        // active request the panel is still waiting on
        assertFalse(state.onReply(oldBox))
        assertTrue(state.onReply(newBox))
    }

    @Test
    fun cancelThenReselectingTheSamePasteCannotAdoptTheOldReply() {
        val state = SixelFetchState()
        val cancelled = assertNotNull(state.nextRequest(7, 60, 9))
        state.cancel()
        // Re-request after cancel must not be blocked by stale bookkeeping...
        val fresh = assertNotNull(state.nextRequest(7, 60, 9))
        // ...and the generation keeps the cancelled attempt's key distinct
        // even though paste and box are identical
        assertNotEquals(cancelled, fresh)
        assertFalse(state.onReply(cancelled))
        assertTrue(state.onReply(fresh))
    }

    @Test
    fun identicalInFlightRequestIsReusedAndItsReplyCompletesExactlyOnce() {
        val state = SixelFetchState()
        val key = assertNotNull(state.nextRequest(7, 60, 9))
        // Rescheduled draws for the same paste and box ride the in-flight
        // request instead of relaunching a duplicate transcode
        assertNull(state.nextRequest(7, 60, 9))
        assertTrue(state.onReply(key))
        // A duplicate reply cannot complete a second time
        assertFalse(state.onReply(key))
        // Once answered, the same request may be issued again (e.g. a repaint
        // erased the drawn image)
        assertNotNull(state.nextRequest(7, 60, 9))
    }

    @Test
    fun selectionChangeSupersedesTheInFlightPaste() {
        val state = SixelFetchState()
        val first = assertNotNull(state.nextRequest(7, 60, 9))
        val second = assertNotNull(state.nextRequest(8, 60, 9))
        assertFalse(state.onReply(first))
        assertTrue(state.onReply(second))
    }
}
