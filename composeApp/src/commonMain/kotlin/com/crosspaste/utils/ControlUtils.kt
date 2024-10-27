package com.crosspaste.utils

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

expect fun getControlUtils(): ControlUtils

interface ControlUtils {

    suspend fun <T> ensureMinExecutionTime(
        delayTime: Int = 20,
        action: suspend () -> T,
    ): T

    suspend fun <T> exponentialBackoffUntilValid(
        initTime: Long,
        maxTime: Long,
        isValidResult: (T) -> Boolean,
        action: suspend () -> T,
    ): T

    fun <T> blockEnsureMinExecutionTime(
        delayTime: Int = 20,
        action: () -> T,
    ): T

    fun <T> blockExponentialBackoffUntilValid(
        initTime: Long,
        maxTime: Long,
        isValidResult: (T) -> Boolean,
        action: () -> T,
    ): T
}

fun <T> Flow<T>.equalDebounce(
    durationMillis: Long,
    isEqual: (T, T) -> Boolean = { a, b -> a == b },
): Flow<T> =
    flow {
        var lastItem: T? = null
        var lastEmitTime: Long = 0

        collect { value ->
            val currentTime = System.currentTimeMillis()
            val shouldEmit =
                lastItem?.let { last ->
                    !isEqual(last, value) || (currentTime - lastEmitTime) >= durationMillis
                } != false

            if (shouldEmit) {
                lastItem = value
                lastEmitTime = currentTime
                emit(value)
            }
        }
    }
