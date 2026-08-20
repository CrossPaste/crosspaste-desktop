package com.crosspaste.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DevConfigOptionalPropertyTest {

    @Test
    fun `null and blank values resolve to null`() {
        assertNull(developmentOptionalProperty(null))
        assertNull(developmentOptionalProperty(""))
        assertNull(developmentOptionalProperty("   "))
    }

    @Test
    fun `values are trimmed`() {
        assertEquals(
            "/path/to/crosspaste-mouse",
            developmentOptionalProperty(" /path/to/crosspaste-mouse "),
        )
    }
}
