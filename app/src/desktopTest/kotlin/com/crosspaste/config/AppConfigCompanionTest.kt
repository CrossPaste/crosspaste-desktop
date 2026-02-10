package com.crosspaste.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppConfigCompanionTest {

    // --- toBoolean: converts various types to Boolean ---

    @Test
    fun `toBoolean from Boolean passes through`() {
        assertTrue(AppConfig.toBoolean(true))
        assertFalse(AppConfig.toBoolean(false))
    }

    @Test
    fun `toBoolean from String parses true and false`() {
        assertTrue(AppConfig.toBoolean("true"))
        assertFalse(AppConfig.toBoolean("false"))
        assertFalse(AppConfig.toBoolean("invalid"))
    }

    @Test
    fun `toBoolean from Int treats non-zero as true`() {
        assertTrue(AppConfig.toBoolean(1))
        assertTrue(AppConfig.toBoolean(-1))
        assertFalse(AppConfig.toBoolean(0))
    }

    @Test
    fun `toBoolean from Long treats non-zero as true`() {
        assertTrue(AppConfig.toBoolean(1L))
        assertFalse(AppConfig.toBoolean(0L))
    }

    @Test
    fun `toBoolean from unsupported type returns false`() {
        assertFalse(AppConfig.toBoolean(3.14))
        assertFalse(AppConfig.toBoolean(listOf("a")))
    }

    // --- toInt: converts various types to Int ---

    @Test
    fun `toInt from Int passes through`() {
        assertEquals(42, AppConfig.toInt(42))
    }

    @Test
    fun `toInt from String parses valid integers`() {
        assertEquals(42, AppConfig.toInt("42"))
        assertEquals(0, AppConfig.toInt("invalid"))
    }

    @Test
    fun `toInt from Boolean maps to 0 or 1`() {
        assertEquals(1, AppConfig.toInt(true))
        assertEquals(0, AppConfig.toInt(false))
    }

    @Test
    fun `toInt from Long truncates to int`() {
        assertEquals(42, AppConfig.toInt(42L))
    }

    @Test
    fun `toInt from unsupported type returns 0`() {
        assertEquals(0, AppConfig.toInt(3.14))
    }

    // --- toLong: converts various types to Long ---

    @Test
    fun `toLong from Long passes through`() {
        assertEquals(42L, AppConfig.toLong(42L))
    }

    @Test
    fun `toLong from String parses valid longs`() {
        assertEquals(42L, AppConfig.toLong("42"))
        assertEquals(0L, AppConfig.toLong("invalid"))
    }

    @Test
    fun `toLong from Int widens to long`() {
        assertEquals(42L, AppConfig.toLong(42))
    }

    @Test
    fun `toLong from unsupported type returns 0`() {
        assertEquals(0L, AppConfig.toLong(3.14))
    }

    // --- toString: converts any to String ---

    @Test
    fun `toString delegates to Any toString`() {
        assertEquals("42", AppConfig.toString(42))
        assertEquals("true", AppConfig.toString(true))
        assertEquals("hello", AppConfig.toString("hello"))
    }
}
