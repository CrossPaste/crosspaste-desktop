package com.crosspaste.net.clientapi

import com.crosspaste.exception.StandardErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClientApiResultTest {

    @Test
    fun `SuccessResult stores and retrieves typed value`() {
        val result = SuccessResult("hello")
        assertEquals("hello", result.getResult<String>())
    }

    @Test
    fun `SuccessResult getResult with wrong type throws IllegalStateException`() {
        val result = SuccessResult(42)
        assertFailsWith<IllegalStateException> {
            result.getResult<String>()
        }
    }

    @Test
    fun `SuccessResult handles null value`() {
        val result = SuccessResult(null)
        assertEquals(null, result.getResult<String?>())
    }

    @Test
    fun `createFailureResult maps known error code from FailResponse`() {
        val failResponse = FailResponse(errorCode = 2007, message = "encrypt failed")
        val result = createFailureResult(failResponse)
        assertEquals(StandardErrorCode.ENCRYPT_FAIL.toErrorCode(), result.exception.getErrorCode())
    }

    @Test
    fun `createFailureResult falls back to UNKNOWN_ERROR for unrecognized code`() {
        val failResponse = FailResponse(errorCode = 99999, message = "something")
        val result = createFailureResult(failResponse)
        assertEquals(StandardErrorCode.UNKNOWN_ERROR.toErrorCode(), result.exception.getErrorCode())
    }

    @Test
    fun `createFailureResult from ErrorCodeSupplier preserves message`() {
        val result = createFailureResult(StandardErrorCode.NOT_FOUND_API, "not found")
        assertEquals(StandardErrorCode.NOT_FOUND_API.toErrorCode(), result.exception.getErrorCode())
        assertTrue(result.exception.message?.contains("not found") == true)
    }

    @Test
    fun `createFailureResult from ErrorCodeSupplier preserves cause`() {
        val cause = RuntimeException("root cause")
        val result = createFailureResult(StandardErrorCode.UNKNOWN_ERROR, cause)
        assertEquals(StandardErrorCode.UNKNOWN_ERROR.toErrorCode(), result.exception.getErrorCode())
        assertEquals(cause, result.exception.cause)
    }
}
