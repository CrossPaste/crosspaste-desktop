package com.clipevery

import com.clipevery.platform.currentPlatform
import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformTest {

    @Test
    fun testPlatform() {
        val platform = currentPlatform()
        assertTrue(platform.name != "Unknown")
    }
}