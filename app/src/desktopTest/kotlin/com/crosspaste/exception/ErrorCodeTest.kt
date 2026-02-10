package com.crosspaste.exception

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ErrorCodeTest {

    @Test
    fun `ErrorCode rejects negative code`() {
        assertFailsWith<IllegalArgumentException> {
            ErrorCode(-1, "NEGATIVE", ErrorType.INTERNAL_ERROR)
        }
    }

    @Test
    fun `ErrorCode allows zero code`() {
        val errorCode = ErrorCode(0, "ZERO", ErrorType.INTERNAL_ERROR)
        assertEquals(0, errorCode.code)
    }

    @Test
    fun `StandardErrorCode entries have unique codes`() {
        val codes = StandardErrorCode.entries.map { it.getCode() }
        assertEquals(codes.size, codes.distinct().size, "All error codes should be unique")
    }

    @Test
    fun `standardErrorCodeMap contains all entries`() {
        for (entry in StandardErrorCode.entries) {
            assertTrue(
                standardErrorCodeMap.containsKey(entry.getCode()),
                "standardErrorCodeMap should contain ${entry.name}",
            )
        }
    }

    @Test
    fun `standardErrorCodeMap maps correctly`() {
        assertEquals(StandardErrorCode.UNKNOWN_ERROR, standardErrorCodeMap[0])
        assertEquals(StandardErrorCode.ENCRYPT_FAIL, standardErrorCodeMap[2007])
        assertEquals(StandardErrorCode.DECRYPT_FAIL, standardErrorCodeMap[2008])
    }
}
