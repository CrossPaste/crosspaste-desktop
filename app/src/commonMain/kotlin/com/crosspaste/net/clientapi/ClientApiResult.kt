package com.crosspaste.net.clientapi

import com.crosspaste.exception.ErrorCodeSupplier
import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.exception.standardErrorCodeMap
import com.crosspaste.net.exception.ExceptionHandler
import io.github.oshai.kotlinlogging.KLogger
import io.ktor.client.call.*
import io.ktor.client.statement.*
import kotlinx.serialization.Serializable

interface ClientApiResult

class SuccessResult(
    @PublishedApi internal val value: Any? = null,
) : ClientApiResult {
    inline fun <reified T> getResult(): T {
        check(value is T) { "Expected ${T::class}, got ${value?.let { it::class }}" }
        return value
    }
}

class FailureResult(
    val exception: PasteException,
) : ClientApiResult

object ConnectionRefused : ClientApiResult

object EncryptFail : ClientApiResult

object DecryptFail : ClientApiResult

object UnknownError : ClientApiResult

fun createFailureResult(failResponse: FailResponse): FailureResult {
    val supplier = standardErrorCodeMap[failResponse.errorCode] ?: StandardErrorCode.UNKNOWN_ERROR
    return FailureResult(
        PasteException(
            supplier.toErrorCode(),
            failResponse.message,
        ),
    )
}

fun createFailureResult(
    errorCodeSupplier: ErrorCodeSupplier,
    message: String,
): FailureResult = FailureResult(PasteException(errorCodeSupplier.toErrorCode(), message))

fun createFailureResult(
    errorCodeSupplier: ErrorCodeSupplier,
    throwable: Throwable,
): FailureResult = FailureResult(PasteException(errorCodeSupplier.toErrorCode(), throwable))

suspend inline fun <T> request(
    logger: KLogger,
    exceptionHandler: ExceptionHandler,
    request: () -> HttpResponse,
    transformData: (HttpResponse) -> T,
): ClientApiResult =
    try {
        val response = request()
        logger.debug { "response status: ${response.call.request.url} ${response.status}" }
        if (response.status.value == 404) {
            createFailureResult(StandardErrorCode.NOT_FOUND_API, "Not found Api")
        } else if (response.status.value != 200) {
            val failResponse = response.body<FailResponse>()
            logger.error { "request error: $failResponse" }
            createFailureResult(failResponse)
        } else {
            SuccessResult(transformData(response))
        }
    } catch (e: Exception) {
        logger.error(e) { "request error" }
        if (exceptionHandler.isConnectionRefused(e)) {
            ConnectionRefused
        } else if (exceptionHandler.isEncryptFail(e)) {
            EncryptFail
        } else if (exceptionHandler.isDecryptFail(e)) {
            DecryptFail
        } else {
            UnknownError
        }
    }

@Serializable
data class FailResponse(
    val errorCode: Int,
    val message: String = "",
) {

    override fun toString(): String = "FailResponse(errorCode=$errorCode, message='$message')"
}
