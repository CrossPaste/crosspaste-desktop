package com.crosspaste.net.clientapi

import com.crosspaste.exception.ErrorCodeSupplier
import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.exception.standardErrorCodeMap
import com.crosspaste.utils.getExceptionUtils
import io.github.oshai.kotlinlogging.KLogger
import io.ktor.client.call.*
import io.ktor.client.statement.*
import kotlinx.serialization.Serializable

interface ClientApiResult

@Suppress("UNCHECKED_CAST")
class SuccessResult(private val result: Any? = null) : ClientApiResult {
    fun <T> getResult(): T {
        return result as T
    }
}

class FailureResult(val exception: PasteException) : ClientApiResult

object ConnectionRefused : ClientApiResult

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
): FailureResult {
    return FailureResult(PasteException(errorCodeSupplier.toErrorCode(), message))
}

fun createFailureResult(
    errorCodeSupplier: ErrorCodeSupplier,
    throwable: Throwable,
): FailureResult {
    return FailureResult(PasteException(errorCodeSupplier.toErrorCode(), throwable))
}

suspend inline fun <T> request(
    logger: KLogger,
    request: () -> HttpResponse,
    transformData: (HttpResponse) -> T,
): ClientApiResult {
    try {
        val response = request()
        if (response.status.value == 404) {
            return createFailureResult(StandardErrorCode.NOT_FOUND_API, "Not found Api")
        } else if (response.status.value != 200) {
            val failResponse = response.body<FailResponse>()
            return createFailureResult(failResponse)
        }
        return SuccessResult(transformData(response))
    } catch (e: Exception) {
        logger.error(e) { "request error" }
        val exceptionUtils = getExceptionUtils()
        return if (exceptionUtils.isConnectionRefused(e)) {
            ConnectionRefused
        } else {
            UnknownError
        }
    }
}

@Serializable
data class FailResponse(
    val errorCode: Int,
    val message: String = "",
) {

    override fun toString(): String {
        return "FailResponse(errorCode=$errorCode, message='$message')"
    }
}
