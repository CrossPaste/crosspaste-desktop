package com.clipevery.net.exception

import com.clipevery.exception.StandardErrorCode
import com.clipevery.utils.failResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.InvalidMessageException
import org.signal.libsignal.protocol.UntrustedIdentityException

fun StatusPagesConfig.signalExceptionHandler() {
    val logger = KotlinLogging.logger {}

    exception(InvalidMessageException::class) { call, invalidMessageException ->
        logger.error(invalidMessageException) { "Invalid message" }
        failResponse(call, StandardErrorCode.SIGNAL_INVALID_MESSAGE.toErrorCode())
    }

    exception(InvalidKeyIdException::class) { call, invalidKeyIdException ->
        logger.error(invalidKeyIdException) { "Invalid key id" }
        failResponse(call, StandardErrorCode.SIGNAL_INVALID_KEY_ID.toErrorCode())
    }

    exception(InvalidKeyException::class) { call, invalidKeyException ->
        logger.error(invalidKeyException) { "Invalid key" }
        failResponse(call, StandardErrorCode.SIGNAL_INVALID_KEY.toErrorCode())
    }

    exception(UntrustedIdentityException::class) { call, untrustedIdentityException ->
        logger.error(untrustedIdentityException) { "Untrusted identity" }
        failResponse(call, StandardErrorCode.SIGNAL_UNTRUSTED_IDENTITY.toErrorCode())
    }
}
