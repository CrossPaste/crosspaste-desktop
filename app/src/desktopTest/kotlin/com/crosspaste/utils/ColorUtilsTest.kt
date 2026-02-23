package com.crosspaste.utils

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ColorUtilsTest {
    private val colorParser = getColorParser()

    @Test
    fun testToColorHex6() {
        // Valid 6-digit hex
        assertEquals(Color(0xFFFF0000), colorParser.toColor("#FF0000"))
        assertEquals(Color(0xFF00FF00), colorParser.toColor("#00FF00"))
        assertEquals(Color(0xFF0000FF), colorParser.toColor("#0000FF"))

        // Valid 6-digit hex without #
        assertEquals(Color(0xFFFF0000), colorParser.toColor("FF0000"))
        assertEquals(Color(0xFF00FF00), colorParser.toColor("00FF00"))

        // Case insensitive
        assertEquals(Color(0xFFFF0000), colorParser.toColor("#ff0000"))
        assertEquals(Color(0xFF00FF00), colorParser.toColor("#00ff00"))
    }

    @Test
    fun testToColorHex8() {
        // Valid 8-digit hex
        assertEquals(Color(0x80FF0000L), colorParser.toColor("#FF000080"))
        assertEquals(Color(0x8000FF00L), colorParser.toColor("#00FF0080"))
        assertEquals(Color(0x800000FFL), colorParser.toColor("#0000FF80"))

        // Valid 8-digit hex without #
        assertEquals(Color(0x80FF0000L), colorParser.toColor("FF000080"))

        // Case insensitive
        assertEquals(Color(0x80FF0000L), colorParser.toColor("#ff000080"))
    }

    @Test
    fun testtoColor_RGB() {
        // Valid RGB format
        assertEquals(Color(0xFFFF0000), colorParser.toColor("rgb(255, 0, 0)"))
        assertEquals(Color(0xFF00FF00), colorParser.toColor("rgb(0, 255, 0)"))
        assertEquals(Color(0xFF0000FF), colorParser.toColor("rgb(0, 0, 255)"))

        // RGB with spaces
        assertEquals(Color(0xFFFF0000), colorParser.toColor("rgb( 255 , 0 , 0 )"))

        // RGB with minimum values
        assertEquals(Color(0xFF000000), colorParser.toColor("rgb(0, 0, 0)"))
    }

    @Test
    fun testToColorRGBA() {
        // Valid RGBA format
        assertEquals(Color(0xFFFF0000), colorParser.toColor("rgba(255, 0, 0, 1)"))
        assertEquals(Color(0x80FF0000), colorParser.toColor("rgba(255, 0, 0, 0.5)"))
        assertEquals(Color(0x00FF0000), colorParser.toColor("rgba(255, 0, 0, 0)"))

        // RGBA with spaces
        assertEquals(Color(0xFFFF0000), colorParser.toColor("rgba( 255 , 0 , 0 , 1.0 )"))

        // RGBA with different alpha formats
        assertEquals(Color(0x80FF0000), colorParser.toColor("rgba(255, 0, 0, .5)"))
    }

    @Test
    fun testToColorInvalidInput() {
        // Empty input
        assertNull(colorParser.toColor(""))

        // Invalid hex
        assertNull(colorParser.toColor("#ZZZZZZ"))
        assertNull(colorParser.toColor("#12345")) // Too short
        assertNull(colorParser.toColor("#1234567")) // Wrong length

        // Invalid RGB
        assertNull(colorParser.toColor("rgb(0, 0)")) // Missing value
        assertNull(colorParser.toColor("rgb(0, 0, 0, 0)")) // Too many values
        assertNull(colorParser.toColor("rgb(a, b, c)")) // Invalid numbers

        // Invalid RGBA
        assertNull(colorParser.toColor("rgba(255, 0, 0, -0.5)")) // Negative alpha
        assertNull(colorParser.toColor("rgba(0, 0, 0)")) // Missing alpha
        assertNull(colorParser.toColor("rgba(a, b, c, d)")) // Invalid numbers

        // Completely invalid format
        assertNull(colorParser.toColor("not a color"))
    }
}
