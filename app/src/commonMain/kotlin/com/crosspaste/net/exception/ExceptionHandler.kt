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

    // Crypto failures rarely surface as the top-level exception: a decrypt that
    // happens inside a body-transform channel arrives wrapped (e.g. Ktor's
    // ClosedByteChannelException with the PasteException as its cause), so every
    // classifier below walks the cause chain instead of matching only `e`.
    private fun causeChain(e: Throwable): Sequence<Throwable> =
        generateSequence(e) { current ->
            current.cause?.takeIf { it !== current }
        }.take(MAX_CAUSE_DEPTH)

    fun isEncryptFail(e: Throwable): Boolean =
        causeChain(e).any {
            it is PasteException && it.getErrorCode() == StandardErrorCode.ENCRYPT_FAIL.toErrorCode()
        }

    fun isDecryptFail(e: Throwable): Boolean =
        causeChain(e).any {
            when (it) {
                is PasteException -> it.getErrorCode() == StandardErrorCode.DECRYPT_FAIL.toErrorCode()
                is CannotTransformContentToTypeException -> true
                else -> false
            }
        }

    companion object {
        private const val MAX_CAUSE_DEPTH = 10
    }
}
