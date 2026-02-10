package com.crosspaste.exception

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasteExceptionTest {

    @Test
    fun `match returns true for matching StandardErrorCode`() {
        val exception = PasteException(StandardErrorCode.ENCRYPT_FAIL.toErrorCode(), "test")
        assertTrue(exception.match(StandardErrorCode.ENCRYPT_FAIL))
    }

    @Test
    fun `match returns false for non-matching StandardErrorCode`() {
        val exception = PasteException(StandardErrorCode.ENCRYPT_FAIL.toErrorCode(), "test")
        assertFalse(exception.match(StandardErrorCode.DECRYPT_FAIL))
    }
}
