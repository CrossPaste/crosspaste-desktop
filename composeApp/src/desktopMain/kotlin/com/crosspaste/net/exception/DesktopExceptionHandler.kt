package com.crosspaste.net.exception

import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.utils.failResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.statuspages.*

class DesktopExceptionHandler : ExceptionHandler {
    override fun handler(): StatusPagesConfig.() -> Unit =
        {
            val logger = KotlinLogging.logger {}

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
}
