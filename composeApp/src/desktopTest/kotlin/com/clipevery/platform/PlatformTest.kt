package com.clipevery.platform

import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformTest {

    @Test
    fun testPlatform() {
        val platform = currentPlatform()
        assertTrue(platform.name != "Unknown")
    }
}
