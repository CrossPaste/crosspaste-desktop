package com.crosspaste.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ColorUtilsTest {
    private val colorUtils = getColorUtils()

    @Test
    fun testTryCovertToColor_Hex6() {
        // Valid 6-digit hex
        assertEquals(0xFFFF0000, colorUtils.tryCovertToColor("#FF0000"))
        assertEquals(0xFF00FF00, colorUtils.tryCovertToColor("#00FF00"))
        assertEquals(0xFF0000FF, colorUtils.tryCovertToColor("#0000FF"))

        // Valid 6-digit hex without #
        assertEquals(0xFFFF0000, colorUtils.tryCovertToColor("FF0000"))
        assertEquals(0xFF00FF00, colorUtils.tryCovertToColor("00FF00"))

        // Case insensitive
        assertEquals(0xFFFF0000, colorUtils.tryCovertToColor("#ff0000"))
        assertEquals(0xFF00FF00, colorUtils.tryCovertToColor("#00ff00"))
    }

    @Test
    fun testTryCovertToColor_Hex8() {
        // Valid 8-digit hex
        assertEquals(0x80FF0000L, colorUtils.tryCovertToColor("#80FF0000"))
        assertEquals(0x8000FF00L, colorUtils.tryCovertToColor("#8000FF00"))
        assertEquals(0x800000FFL, colorUtils.tryCovertToColor("#800000FF"))

        // Valid 8-digit hex without #
        assertEquals(0x80FF0000L, colorUtils.tryCovertToColor("80FF0000"))

        // Case insensitive
        assertEquals(0x80FF0000L, colorUtils.tryCovertToColor("#80ff0000"))
    }

    @Test
    fun testTryCovertToColor_RGB() {
        // Valid RGB format
        assertEquals(0xFFFF0000, colorUtils.tryCovertToColor("rgb(255, 0, 0)"))
        assertEquals(0xFF00FF00, colorUtils.tryCovertToColor("rgb(0, 255, 0)"))
        assertEquals(0xFF0000FF, colorUtils.tryCovertToColor("rgb(0, 0, 255)"))

        // RGB with spaces
        assertEquals(0xFFFF0000, colorUtils.tryCovertToColor("rgb( 255 , 0 , 0 )"))

        // RGB with minimum values
        assertEquals(0xFF000000, colorUtils.tryCovertToColor("rgb(0, 0, 0)"))
    }

    @Test
    fun testTryCovertToColor_RGBA() {
        // Valid RGBA format
        assertEquals(0xFFFF0000, colorUtils.tryCovertToColor("rgba(255, 0, 0, 1)"))
        assertEquals(0x80FF0000, colorUtils.tryCovertToColor("rgba(255, 0, 0, 0.5)"))
        assertEquals(0x00FF0000, colorUtils.tryCovertToColor("rgba(255, 0, 0, 0)"))

        // RGBA with spaces
        assertEquals(0xFFFF0000, colorUtils.tryCovertToColor("rgba( 255 , 0 , 0 , 1.0 )"))

        // RGBA with different alpha formats
        assertEquals(0x80FF0000, colorUtils.tryCovertToColor("rgba(255, 0, 0, .5)"))
    }

    @Test
    fun testTryCovertToColor_InvalidInput() {
        // Empty input
        assertNull(colorUtils.tryCovertToColor(""))

        // Invalid hex
        assertNull(colorUtils.tryCovertToColor("#ZZZZZZ"))
        assertNull(colorUtils.tryCovertToColor("#12345")) // Too short
        assertNull(colorUtils.tryCovertToColor("#1234567")) // Wrong length

        // Invalid RGB
        assertNull(colorUtils.tryCovertToColor("rgb(256, 0, 0)")) // Value > 255
        assertNull(colorUtils.tryCovertToColor("rgb(-1, 0, 0)")) // Negative value
        assertNull(colorUtils.tryCovertToColor("rgb(0, 0)")) // Missing value
        assertNull(colorUtils.tryCovertToColor("rgb(0, 0, 0, 0)")) // Too many values
        assertNull(colorUtils.tryCovertToColor("rgb(a, b, c)")) // Invalid numbers

        // Invalid RGBA
        assertNull(colorUtils.tryCovertToColor("rgba(255, 0, 0, 2)")) // Alpha > 1
        assertNull(colorUtils.tryCovertToColor("rgba(255, 0, 0, -0.5)")) // Negative alpha
        assertNull(colorUtils.tryCovertToColor("rgba(0, 0, 0)")) // Missing alpha
        assertNull(colorUtils.tryCovertToColor("rgba(a, b, c, d)")) // Invalid numbers

        // Completely invalid format
        assertNull(colorUtils.tryCovertToColor("not a color"))
    }
}
