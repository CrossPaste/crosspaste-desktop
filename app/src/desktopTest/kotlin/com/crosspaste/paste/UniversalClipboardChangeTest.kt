package com.crosspaste.paste

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class UniversalClipboardChangeTest {

    private val timeSource = TestTimeSource()
    private val change = UniversalClipboardChange(window = 10.seconds, timeSource = timeSource)

    @Test
    fun nothingRecordedIsNotCurrent() {
        assertFalse(change.isCurrent(5))
    }

    @Test
    fun recordedChangeIsCurrentWhileCountUnchangedWithinWindow() {
        change.record(5)
        timeSource += 9.seconds
        assertTrue(change.isCurrent(5))
    }

    @Test
    fun laterPasteboardChangeIsNotCurrent() {
        change.record(5)
        assertFalse(change.isCurrent(6))
    }

    @Test
    fun recordedChangeExpiresAfterWindow() {
        change.record(5)
        timeSource += 11.seconds
        assertFalse(change.isCurrent(5))
    }
}
