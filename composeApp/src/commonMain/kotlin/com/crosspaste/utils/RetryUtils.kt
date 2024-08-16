package com.crosspaste.utils

expect fun getRetryUtils(): RetryUtils

interface RetryUtils {
    fun <T> retry(
        maxRetries: Int,
        block: () -> T,
    ): T?
}
