package com.crosspaste.utils

import androidx.compose.ui.graphics.Color
import com.crosspaste.utils.ColorConversion.toRGBString
import kotlin.test.DefaultAsserter.assertEquals
import kotlin.test.DefaultAsserter.assertNotNull
import kotlin.test.DefaultAsserter.assertNull
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.Test

class HtmlColorUtilsTest {

    @Test
    fun `test RGB color extraction from style attribute`() {
        val html =
            """
            <h1 style="background-color: rgb(255, 255, 255);">Test</h1>
            """.trimIndent()

        val backgroundColor = HtmlColorUtils.getBackgroundColor(html)

        assertEquals(
            "Expected white background color, but got ${backgroundColor?.toRGBString()}",
            Color.White,
            backgroundColor,
        )
    }

    @Test
    fun `test RGBA color extraction with opacity`() {
        val html =
            """
            <span style="background-color: rgba(25, 25, 28, 0.05);">Test</span>
            """.trimIndent()

        val backgroundColor = HtmlColorUtils.getBackgroundColor(html)

        assertNotNull(
            "Expected to extract RGBA color, but got null",
            backgroundColor,
        )

        backgroundColor?.let {
            val rgbString = it.toRGBString()
            assertTrue(
                "Expected RGBA color with alpha channel, but got $rgbString",
                rgbString.contains("0.05"),
            )
        }
    }

    @Test
    fun `test named color extraction - transparent`() {
        val html =
            """
            <pre style="background: transparent;">Code</pre>
            """.trimIndent()

        val backgroundColor = HtmlColorUtils.getBackgroundColor(html)

        assertEquals(
            "Expected transparent background, but got ${backgroundColor?.toRGBString()}",
            Color.Transparent,
            backgroundColor,
        )
    }

    @Test
    fun `test complex style attribute with multiple properties`() {
        val html =
            """
            <div style="color: rgb(31, 35, 40); font-size: 32px; background-color: rgb(240, 240, 240);">
                Complex styled element
            </div>
            """.trimIndent()

        val backgroundColor = HtmlColorUtils.getBackgroundColor(html)

        assertNotNull(
            "Expected to extract background color from complex style, but got null",
            backgroundColor,
        )

        backgroundColor?.let {
            val rgbString = it.toRGBString()
            assertTrue(
                "Expected light gray background (240, 240, 240), but got $rgbString",
                rgbString.contains("240"),
            )
        }
    }

    @Test
    fun `test missing background color`() {
        val html =
            """
            <p style="color: black; font-size: 14px;">No background</p>
            """.trimIndent()

        val backgroundColor = HtmlColorUtils.getBackgroundColor(html)

        assertNull(
            "Expected null for missing background color, but got ${backgroundColor?.toRGBString()}",
            backgroundColor,
        )
    }

    @Test
    fun `test hex color extraction`() {
        val html =
            """
            <div style="background-color: #FF0000;">Red background</div>
            """.trimIndent()

        val backgroundColor = HtmlColorUtils.getBackgroundColor(html)

        assertNotNull(
            "Expected to extract hex color, but got null",
            backgroundColor,
        )

        backgroundColor?.let {
            val rgbString = it.toRGBString()
            assertTrue(
                "Expected red color (255, 0, 0), but got $rgbString",
                rgbString.contains("RGBA(255, 0, 0"),
            )
        }
    }

    @Test
    fun `test short hex color extraction`() {
        val html =
            """
            <div style="background: #FFF;">White background</div>
            """.trimIndent()

        val backgroundColor = HtmlColorUtils.getBackgroundColor(html)

        assertEquals(
            "Expected white color from short hex, but got ${backgroundColor?.toRGBString()}",
            Color.White,
            backgroundColor,
        )
    }

    @Test
    fun `test named color extraction - common colors`() {
        val testCases =
            listOf(
                "white" to Color.White,
                "black" to Color.Black,
                "red" to Color.Red,
                "blue" to Color.Blue,
            )

        testCases.forEach { (colorName, expectedColor) ->
            val html = """<div style="background-color: $colorName;">Test</div>"""
            val backgroundColor = HtmlColorUtils.getBackgroundColor(html)

            assertEquals(
                "Expected $colorName color, but got ${backgroundColor?.toRGBString()}",
                expectedColor,
                backgroundColor,
            )
        }
    }

    @Test
    fun `test malformed HTML handling`() {
        val html =
            """
            <div style="background-color: rgb(255, 255, 255"
            """.trimIndent()

        val backgroundColor = HtmlColorUtils.getBackgroundColor(html)

        // Depending on implementation, this might return null or handle gracefully
        // Adjust assertion based on expected behavior
        println("Malformed HTML result: ${backgroundColor?.toRGBString()}")
    }

    @Test
    fun `test multiple elements with different backgrounds`() {
        val html =
            """
            <div style="background-color: rgb(100, 100, 100);">
                <span style="background-color: rgb(200, 200, 200);">Nested</span>
            </div>
            """.trimIndent()

        val backgroundColor = HtmlColorUtils.getBackgroundColor(html)

        assertNotNull(
            "Expected to extract first background color, but got null",
            backgroundColor,
        )

        // Test should verify which element's background is extracted (likely the first one)
        backgroundColor?.let {
            val rgbString = it.toRGBString()
            println("Extracted background from multiple elements: $rgbString")
        }
    }

    @Test
    fun `test case insensitive style attributes`() {
        val html =
            """
            <div style="BACKGROUND-COLOR: RGB(128, 128, 128);">Test</div>
            """.trimIndent()

        val backgroundColor = HtmlColorUtils.getBackgroundColor(html)

        assertNotNull(
            "Expected to handle case-insensitive attributes, but got null",
            backgroundColor,
        )

        backgroundColor?.let {
            val rgbString = it.toRGBString()
            assertTrue(
                "Expected gray color (128, 128, 128), but got $rgbString",
                rgbString.contains("128"),
            )
        }
    }

    @Test
    fun `test background shorthand edge cases`() {
        val testCases =
            listOf(
                "background: url(a.png) no-repeat #00FF00" to Color.Green,
                "background: url('data:image/png;base64,AAAA') red" to Color.Red,
                "background: linear-gradient(to right, rgb(0, 0, 255) 0%, red 100%)" to Color.Blue,
                "background: url(a.png), linear-gradient(white, black)" to Color.White,
                "background-color: black !important" to Color.Black,
                "background-color: #11223344" to Color(0x11, 0x22, 0x33, 0x44),
                "background-color: #1234" to Color(0x11, 0x22, 0x33, 0x44),
                "background: #FFF8" to Color(0xFF, 0xFF, 0xFF, 0x88),
                "/* background: red; */ background: blue" to Color.Blue,
                "background: red; background-color: white" to Color.White,
                // `!important` without a leading space is still valid CSS
                "background-color:red!important" to Color.Red,
                "background:red!important" to Color.Red,
                "background: url(a.png) red!important" to Color.Red,
                "background: linear-gradient(red, blue)!important" to Color.Red,
                // the top-most layer decides what is visible
                "background: linear-gradient(to right, red, blue), #fff" to Color.Red,
                "background: linear-gradient(red, blue), linear-gradient(white, black)" to Color.Red,
                "background: url(a.png), #fff" to Color.White,
                // unquoted URLs may contain balanced parentheses
                "background: url(a(b).png) red" to Color.Red,
            )

        testCases.forEach { (style, expectedColor) ->
            val html = """<div style="$style">Test</div>"""
            val backgroundColor = HtmlColorUtils.getBackgroundColor(html)

            assertEquals(
                "Style [$style]: expected ${expectedColor.toRGBString()}, but got ${backgroundColor?.toRGBString()}",
                expectedColor,
                backgroundColor,
            )
        }
    }

    @Test
    fun `test gradient in background-color is ignored`() {
        // A background-color can never be a gradient, so the declaration is invalid CSS and
        // must not be turned into a solid preview color.
        val html = """<div style="background-color: linear-gradient(red, blue)">Test</div>"""
        val backgroundColor = HtmlColorUtils.getBackgroundColor(html)

        assertNull(
            "Expected null for a gradient in background-color, but got ${backgroundColor?.toRGBString()}",
            backgroundColor,
        )
    }

    @Test
    fun `test bgcolor attribute`() {
        val html = """<table bgcolor="#000000"><tr><td>Test</td></tr></table>"""

        val backgroundColor = HtmlColorUtils.getBackgroundColor(html)

        assertEquals(
            "Expected black from bgcolor, but got ${backgroundColor?.toRGBString()}",
            Color.Black,
            backgroundColor,
        )
    }

    @Test
    fun `test unparseable bgcolor still finds the ancestor background`() {
        // A malformed bgcolor must not stop the search: it used to return White (or Transparent)
        // even though the element itself only inherits its background.
        val html =
            """
            <html style="background-color: #123456">
                <body>
                    <table bgcolor="#zzz"><tr><td>Test</td></tr></table>
                </body>
            </html>
            """.trimIndent()

        assertEquals(
            "Expected the html background color",
            Color(0x12, 0x34, 0x56),
            HtmlColorUtils.getBackgroundColor(html),
        )
    }

    @Test
    fun `test unparseable bgcolor yields no color`() {
        val html = """<table bgcolor="notacolor"><tr><td>Test</td></tr></table>"""

        assertNull(
            "Expected null for an unparseable bgcolor",
            HtmlColorUtils.getBackgroundColor(html),
        )
    }

    @Test
    fun `test css system color still finds the ancestor background`() {
        // System colors have no fixed RGB value, so they must not be reported as transparent
        // and hide a real background set on an ancestor.
        val html =
            """
            <html style="background-color: #123456">
                <body>
                    <div style="background: ButtonFace">Test</div>
                </body>
            </html>
            """.trimIndent()

        assertEquals(
            "Expected the html background color",
            Color(0x12, 0x34, 0x56),
            HtmlColorUtils.getBackgroundColor(html),
        )
    }
}
