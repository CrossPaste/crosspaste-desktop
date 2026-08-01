package com.crosspaste.exception

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ErrorCodeContractTest {

    @Test
    fun `equal instances have equal hashCode`() {
        val first = ErrorCode(2007, "ENCRYPT_FAIL", ErrorType.EXTERNAL_ERROR)
        val second = ErrorCode(2007, "ENCRYPT_FAIL", ErrorType.EXTERNAL_ERROR)

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `independently constructed equal instances are interchangeable in hash sets`() {
        val first = ErrorCode(2007, "ENCRYPT_FAIL", ErrorType.EXTERNAL_ERROR)
        val second = ErrorCode(2007, "ENCRYPT_FAIL", ErrorType.EXTERNAL_ERROR)

        val set = hashSetOf(first)
        assertTrue(second in set)
        assertTrue(set.remove(second))
        assertTrue(set.isEmpty())
    }

    @Test
    fun `independently constructed equal instances are interchangeable as hash map keys`() {
        val first = ErrorCode(2007, "ENCRYPT_FAIL", ErrorType.EXTERNAL_ERROR)
        val second = ErrorCode(2007, "ENCRYPT_FAIL", ErrorType.EXTERNAL_ERROR)

        val map = hashMapOf(first to "value")
        assertEquals("value", map[second])
    }

    @Test
    fun `distinct instances differ in equals`() {
        val base = ErrorCode(2007, "ENCRYPT_FAIL", ErrorType.EXTERNAL_ERROR)

        assertNotEquals(base, ErrorCode(2008, "ENCRYPT_FAIL", ErrorType.EXTERNAL_ERROR))
        assertNotEquals(base, ErrorCode(2007, "DECRYPT_FAIL", ErrorType.EXTERNAL_ERROR))
        assertNotEquals(base, ErrorCode(2007, "ENCRYPT_FAIL", ErrorType.INTERNAL_ERROR))
    }
}
