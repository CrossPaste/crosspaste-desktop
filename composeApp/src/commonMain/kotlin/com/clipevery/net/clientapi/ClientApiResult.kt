package com.clipevery.net.clientapi

import com.clipevery.exception.ClipException
import com.clipevery.exception.ErrorCodeSupplier

interface ClientApiResult

@Suppress("UNCHECKED_CAST")
class SuccessResult(private val result: Any? = null) : ClientApiResult {
    fun <T> getResult(): T {
        return result as T
    }
}

class FailureResult(val throwable: Throwable) : ClientApiResult

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
