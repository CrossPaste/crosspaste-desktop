package com.crosspaste.utils

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ColorUtilsTest {
    private val colorUtils = getColorUtils()

    @Test
    fun testToColorHex6() {
        // Valid 6-digit hex
        assertEquals(Color(0xFFFF0000), colorUtils.toColor("#FF0000"))
        assertEquals(Color(0xFF00FF00), colorUtils.toColor("#00FF00"))
        assertEquals(Color(0xFF0000FF), colorUtils.toColor("#0000FF"))

        // Valid 6-digit hex without #
        assertEquals(Color(0xFFFF0000), colorUtils.toColor("FF0000"))
        assertEquals(Color(0xFF00FF00), colorUtils.toColor("00FF00"))

        // Case insensitive
        assertEquals(Color(0xFFFF0000), colorUtils.toColor("#ff0000"))
        assertEquals(Color(0xFF00FF00), colorUtils.toColor("#00ff00"))
    }

    @Test
    fun testToColorHex8() {
        // Valid 8-digit hex
        assertEquals(Color(0x80FF0000L), colorUtils.toColor("#FF000080"))
        assertEquals(Color(0x8000FF00L), colorUtils.toColor("#00FF0080"))
        assertEquals(Color(0x800000FFL), colorUtils.toColor("#0000FF80"))

        // Valid 8-digit hex without #
        assertEquals(Color(0x80FF0000L), colorUtils.toColor("FF000080"))

        // Case insensitive
        assertEquals(Color(0x80FF0000L), colorUtils.toColor("#ff000080"))
    }

    @Test
    fun testtoColor_RGB() {
        // Valid RGB format
        assertEquals(Color(0xFFFF0000), colorUtils.toColor("rgb(255, 0, 0)"))
        assertEquals(Color(0xFF00FF00), colorUtils.toColor("rgb(0, 255, 0)"))
        assertEquals(Color(0xFF0000FF), colorUtils.toColor("rgb(0, 0, 255)"))

        // RGB with spaces
        assertEquals(Color(0xFFFF0000), colorUtils.toColor("rgb( 255 , 0 , 0 )"))

        // RGB with minimum values
        assertEquals(Color(0xFF000000), colorUtils.toColor("rgb(0, 0, 0)"))
    }

    @Test
    fun testToColorRGBA() {
        // Valid RGBA format
        assertEquals(Color(0xFFFF0000), colorUtils.toColor("rgba(255, 0, 0, 1)"))
        assertEquals(Color(0x80FF0000), colorUtils.toColor("rgba(255, 0, 0, 0.5)"))
        assertEquals(Color(0x00FF0000), colorUtils.toColor("rgba(255, 0, 0, 0)"))

        // RGBA with spaces
        assertEquals(Color(0xFFFF0000), colorUtils.toColor("rgba( 255 , 0 , 0 , 1.0 )"))

        // RGBA with different alpha formats
        assertEquals(Color(0x80FF0000), colorUtils.toColor("rgba(255, 0, 0, .5)"))
    }

    @Test
    fun testToColorInvalidInput() {
        // Empty input
        assertNull(colorUtils.toColor(""))

        // Invalid hex
        assertNull(colorUtils.toColor("#ZZZZZZ"))
        assertNull(colorUtils.toColor("#12345")) // Too short
        assertNull(colorUtils.toColor("#1234567")) // Wrong length

        // Invalid RGB
        assertNull(colorUtils.toColor("rgb(0, 0)")) // Missing value
        assertNull(colorUtils.toColor("rgb(0, 0, 0, 0)")) // Too many values
        assertNull(colorUtils.toColor("rgb(a, b, c)")) // Invalid numbers

        // Invalid RGBA
        assertNull(colorUtils.toColor("rgba(255, 0, 0, -0.5)")) // Negative alpha
        assertNull(colorUtils.toColor("rgba(0, 0, 0)")) // Missing alpha
        assertNull(colorUtils.toColor("rgba(a, b, c, d)")) // Invalid numbers

        // Completely invalid format
        assertNull(colorUtils.toColor("not a color"))
    }
}
