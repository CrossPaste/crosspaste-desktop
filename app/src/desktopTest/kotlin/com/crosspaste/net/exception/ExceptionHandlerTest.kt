package com.crosspaste.net.exception

import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
import io.ktor.server.plugins.CannotTransformContentToTypeException
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExceptionHandlerTest {

    private val handler =
        object : ExceptionHandler() {
            override fun isPortAlreadyInUse(e: Throwable): Boolean =
                e.message?.contains("Address already in use") == true

            override fun isConnectionRefused(e: Throwable): Boolean = e.message?.contains("Connection refused") == true
        }

    @Test
    fun `isEncryptFail returns true for ENCRYPT_FAIL PasteException`() {
        val exception = PasteException(StandardErrorCode.ENCRYPT_FAIL.toErrorCode(), "encrypt error")
        assertTrue(handler.isEncryptFail(exception))
    }

    @Test
    fun `isEncryptFail returns false for other PasteException`() {
        val exception = PasteException(StandardErrorCode.DECRYPT_FAIL.toErrorCode(), "decrypt error")
        assertFalse(handler.isEncryptFail(exception))
    }

    @Test
    fun `isEncryptFail returns false for non-PasteException`() {
        assertFalse(handler.isEncryptFail(RuntimeException("something")))
    }

    @Test
    fun `isDecryptFail returns true for DECRYPT_FAIL PasteException`() {
        val exception = PasteException(StandardErrorCode.DECRYPT_FAIL.toErrorCode(), "decrypt error")
        assertTrue(handler.isDecryptFail(exception))
    }

    @Test
    fun `isDecryptFail returns false for ENCRYPT_FAIL PasteException`() {
        val exception = PasteException(StandardErrorCode.ENCRYPT_FAIL.toErrorCode(), "encrypt error")
        assertFalse(handler.isDecryptFail(exception))
    }

    @Test
    fun `isDecryptFail returns true for CannotTransformContentToTypeException`() {
        val exception = CannotTransformContentToTypeException(typeOf<String>())
        assertTrue(handler.isDecryptFail(exception))
    }

    @Test
    fun `isDecryptFail returns false for generic exception`() {
        assertFalse(handler.isDecryptFail(IllegalStateException("generic")))
    }

    @Test
    fun `isConnectionRefused delegates to subclass`() {
        assertTrue(handler.isConnectionRefused(RuntimeException("Connection refused")))
        assertFalse(handler.isConnectionRefused(RuntimeException("timeout")))
    }

    @Test
    fun `isPortAlreadyInUse delegates to subclass`() {
        assertTrue(handler.isPortAlreadyInUse(RuntimeException("Address already in use")))
        assertFalse(handler.isPortAlreadyInUse(RuntimeException("other")))
    }

    @Test
    fun `isEncryptFail returns false for null throwable message`() {
        assertFalse(handler.isEncryptFail(RuntimeException()))
    }

    @Test
    fun `isDecryptFail returns false for null throwable message`() {
        assertFalse(handler.isDecryptFail(RuntimeException()))
    }
}
