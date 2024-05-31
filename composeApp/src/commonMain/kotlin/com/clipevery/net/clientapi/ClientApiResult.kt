package com.clipevery.net.clientapi

import com.clipevery.exception.ClipException
import com.clipevery.exception.ErrorCodeSupplier
import com.clipevery.exception.StandardErrorCode
import com.clipevery.exception.standardErrorCodeMap
import io.github.oshai.kotlinlogging.KLogger
import io.ktor.client.call.*
import io.ktor.client.statement.*
import kotlinx.serialization.Serializable
import java.net.ConnectException

interface ClientApiResult

@Suppress("UNCHECKED_CAST")
class SuccessResult(private val result: Any? = null) : ClientApiResult {
    fun <T> getResult(): T {
        return result as T
    }
}

class FailureResult(val exception: ClipException) : ClientApiResult

object ConnectionRefused : ClientApiResult

object UnknownError : ClientApiResult

fun createFailureResult(failResponse: FailResponse): FailureResult {
    val supplier = standardErrorCodeMap[failResponse.errorCode] ?: StandardErrorCode.UNKNOWN_ERROR
    return FailureResult(
        ClipException(
            supplier.toErrorCode(),
            failResponse.message,
        ),
    )
}

fun createFailureResult(
    errorCodeSupplier: ErrorCodeSupplier,
    message: String,
): FailureResult {
    return FailureResult(ClipException(errorCodeSupplier.toErrorCode(), message))
}

fun createFailureResult(
    errorCodeSupplier: ErrorCodeSupplier,
    throwable: Throwable,
): FailureResult {
    return FailureResult(ClipException(errorCodeSupplier.toErrorCode(), throwable))
}

suspend inline fun <T> request(
    logger: KLogger,
    request: () -> HttpResponse,
    transformData: (HttpResponse) -> T,
): ClientApiResult {
    try {
        val response = request()
        if (response.status.value != 200) {
            val failResponse = response.body<FailResponse>()
            return createFailureResult(failResponse)
        }
        return SuccessResult(transformData(response))
    } catch (e: Exception) {
        logger.error(e) { "request error" }
        return if (e is ConnectException) {
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
