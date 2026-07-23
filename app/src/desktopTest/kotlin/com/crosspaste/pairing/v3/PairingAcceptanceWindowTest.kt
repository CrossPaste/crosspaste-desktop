package com.crosspaste.pairing.v3

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class PairingAcceptanceWindowTest {

    private var now = 0L

    @Test
    fun testWindowLifecycle() {
        val window = PairingAcceptanceWindow(nowEpochMillis = { now })

        assertFalse(window.isOpen())

        window.open(5.minutes)
        assertTrue(window.isOpen())

        now += 5 * 60 * 1000L
        assertFalse(window.isOpen())

        window.open(5.minutes)
        assertTrue(window.isOpen())
        window.close()
        assertFalse(window.isOpen())
    }
}
