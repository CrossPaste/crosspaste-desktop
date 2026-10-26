package com.crosspaste.ui.base

import androidx.compose.ui.platform.ViewConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SystemDoubleClickIntervalTest {

    private val base =
        object : ViewConfiguration {
            override val longPressTimeoutMillis = 400L
            override val doubleTapTimeoutMillis = 300L
            override val doubleTapMinTimeMillis = 40L
            override val touchSlop = 18f
        }

    @Test
    fun systemIntervalReplacesDoubleTapTimeout() {
        val configuration = withDoubleTapTimeout(base, 500)

        assertEquals(500L, configuration.doubleTapTimeoutMillis)
        assertEquals(base.longPressTimeoutMillis, configuration.longPressTimeoutMillis)
        assertEquals(base.doubleTapMinTimeMillis, configuration.doubleTapMinTimeMillis)
        assertEquals(base.touchSlop, configuration.touchSlop)
    }

    @Test
    fun missingOrInvalidIntervalKeepsBase() {
        assertSame(base, withDoubleTapTimeout(base, null))
        assertSame(base, withDoubleTapTimeout(base, 0))
        assertSame(base, withDoubleTapTimeout(base, "500"))
    }
}
