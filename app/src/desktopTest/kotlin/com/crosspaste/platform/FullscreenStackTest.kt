package com.crosspaste.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FullscreenStackTest {

    private val fullscreen = StackedWindow(fullscreen = true, ordinary = true)
    private val ordinary = StackedWindow(fullscreen = false, ordinary = true)
    private val overlay = StackedWindow(fullscreen = false, ordinary = false)

    @Test
    fun `full-screen window under overlays counts`() {
        assertTrue(isFullscreenOnTop(sequenceOf(overlay, overlay, fullscreen)))
    }

    @Test
    fun `ordinary window in front of a full-screen one does not`() {
        assertFalse(isFullscreenOnTop(sequenceOf(overlay, ordinary, fullscreen)))
    }

    @Test
    fun `no full-screen window does not`() {
        assertFalse(isFullscreenOnTop(sequenceOf(overlay)))
        assertFalse(isFullscreenOnTop(emptySequence()))
    }

    @Test
    fun `windows behind the deciding one are never looked at`() {
        val stack =
            sequence {
                yield(ordinary)
                error("walked past the deciding window")
            }
        assertFalse(isFullscreenOnTop(stack))
    }
}
