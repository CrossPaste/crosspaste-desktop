package com.clipevery.net.clientapi

interface ClientApiResult

@Suppress("UNCHECKED_CAST")
class SuccessResult(val result: Any? = null): ClientApiResult {
    fun <T> getResult(): T {
        return result as T
    }
}

class FailureResult(val message: String): ClientApiResult