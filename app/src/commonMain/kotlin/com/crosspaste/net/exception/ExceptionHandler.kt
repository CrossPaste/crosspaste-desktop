package com.crosspaste.net.exception

import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.utils.failResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*

abstract class ExceptionHandler {

    private val logger = KotlinLogging.logger {}

    fun handler(): StatusPagesConfig.() -> Unit =
        {
            exception(PasteException::class) { call, pasteException ->
                if (pasteException.getErrorCode() == StandardErrorCode.ENCRYPT_FAIL.toErrorCode()) {
                    logger.error(pasteException) { "Encrypt fail" }
                } else if (pasteException.getErrorCode() == StandardErrorCode.DECRYPT_FAIL.toErrorCode()) {
                    logger.error(pasteException) { "Decrypt fail" }
                } else {
                    logger.error(pasteException) { "Paste exception" }
                }
                failResponse(call, pasteException.getErrorCode())
            }
        }

    abstract fun isPortAlreadyInUse(e: Throwable): Boolean

    abstract fun isConnectionRefused(e: Throwable): Boolean

    fun isEncryptFail(e: Throwable): Boolean {
        return if (e is PasteException) {
            e.getErrorCode() == StandardErrorCode.ENCRYPT_FAIL.toErrorCode()
        } else {
            false
        }
    }

    fun isDecryptFail(e: Throwable): Boolean {
        return when (e) {
            is PasteException -> {
                e.getErrorCode() == StandardErrorCode.DECRYPT_FAIL.toErrorCode()
            }

            is CannotTransformContentToTypeException -> {
                true
            }

            else -> {
                false
            }
        }
    }
}
