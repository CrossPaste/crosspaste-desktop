package com.crosspaste.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppTokenServiceTest {

    private fun createService(): TestAppTokenService = TestAppTokenService()

    @Test
    fun `initial token is 6-digit all-zeros`() {
        val service = createService()
        val token = service.token.value
        assertEquals(6, token.size)
        assertTrue(token.all { it == '0' })
    }

    @Test
    fun `sameToken matches when integer equals concatenated token digits`() {
        val service = createService()
        // Initial token is all zeros -> "000000" -> 0
        assertTrue(service.sameToken(0))
    }

    @Test
    fun `sameToken rejects non-matching integer`() {
        val service = createService()
        assertFalse(service.sameToken(123456))
    }

    @Test
    fun `token characters are all decimal digits`() {
        val service = createService()
        assertTrue(service.token.value.all { it in '0'..'9' })
    }
}
