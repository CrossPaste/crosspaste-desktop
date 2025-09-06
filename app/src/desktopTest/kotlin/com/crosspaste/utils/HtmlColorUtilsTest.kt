package com.crosspaste.utils

import androidx.compose.ui.graphics.Color
import com.crosspaste.utils.ColorUtils.toRGBString
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
}
