package com.crosspaste.utils

import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

expect fun getControlUtils(): ControlUtils

interface ControlUtils {

    suspend fun <T> ensureMinExecutionTime(
        delayTime: Long = 20L,
        action: suspend () -> T,
    ): T? {
        val start = nowEpochMilliseconds()
        val result =
            runCatching {
                action()
            }.getOrNull()
        val end = nowEpochMilliseconds()

        val remainingDelay = delayTime + start - end

        if (remainingDelay > 0) {
            delay(remainingDelay)
        }
        return result
    }

    suspend fun <T> exponentialBackoffUntilValid(
        initTime: Long,
        maxTime: Long,
        isValidResult: (T) -> Boolean,
        action: suspend () -> T,
    ): T {
        var result = action()
        var sum = 0L
        var waiting = initTime
        while (!isValidResult(result) && sum < maxTime) {
            delay(waiting)
            sum += waiting
            waiting *= 2
            result = action()
        }
        return result
    }
}

fun <T> Flow<T>.equalDebounce(
    durationMillis: Long,
    isEqual: (T, T) -> Boolean = { a, b -> a == b },
): Flow<T> =
    flow {
        var lastItem: T? = null
        var lastEmitTime: Long = 0

        collect { value ->
            val currentTime = nowEpochMilliseconds()
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
