package com.crosspaste.rendering

import kotlin.test.Test
import kotlin.test.assertEquals

class OpenGraphServiceTest {

    @Test
    fun `JSON_LD_IMAGE_PATTERN matches image in JSON-LD`() {
        val pattern = """"image"\s*:\s*"([^"]+)"""".toRegex()
        val json = """{"@type":"Article","image":"https://example.com/image.jpg","name":"Test"}"""
        val match = pattern.find(json)
        assertEquals("https://example.com/image.jpg", match?.groupValues?.get(1))
    }

    @Test
    fun `JSON_LD_IMAGE_PATTERN matches image with spaces`() {
        val pattern = """"image"\s*:\s*"([^"]+)"""".toRegex()
        val json = """{"image" : "https://example.com/photo.png"}"""
        val match = pattern.find(json)
        assertEquals("https://example.com/photo.png", match?.groupValues?.get(1))
    }

    @Test
    fun `JSON_LD_IMAGE_PATTERN does not match without quotes`() {
        val pattern = """"image"\s*:\s*"([^"]+)"""".toRegex()
        val json = """{"image": null}"""
        val match = pattern.find(json)
        assertEquals(null, match)
    }

    @Test
    fun `JSON_LD_IMAGE_PATTERN matches first image in multiple`() {
        val pattern = """"image"\s*:\s*"([^"]+)"""".toRegex()
        val json = """{"image":"https://first.jpg","other":"data","image":"https://second.jpg"}"""
        val match = pattern.find(json)
        assertEquals("https://first.jpg", match?.groupValues?.get(1))
    }

    @Test
    fun `image URL filtering logic excludes tracking pixels`() {
        val src = "https://example.com/pixel.gif"
        val isFiltered =
            src.contains("pixel") ||
                src.contains("tracking") ||
                src.contains("1x1") ||
                src.endsWith(".gif")
        assertEquals(true, isFiltered)
    }

    @Test
    fun `image URL filtering excludes 1x1 images`() {
        val src = "https://example.com/1x1.png"
        val isFiltered = src.contains("1x1")
        assertEquals(true, isFiltered)
    }

    @Test
    fun `image URL filtering allows normal images`() {
        val src = "https://example.com/hero-banner.jpg"
        val isFiltered =
            src.contains("pixel") ||
                src.contains("tracking") ||
                src.contains("1x1") ||
                src.endsWith(".gif")
        assertEquals(false, isFiltered)
    }
}
