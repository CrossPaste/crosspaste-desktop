package com.crosspaste.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun getControlUtils(): ControlUtils {
    return ControlUtils
}

object ControlUtils {

    suspend fun <T> ensureMinExecutionTime(
        delayTime: Int = 20,
        action: suspend () -> T,
    ): T {
        val start = System.currentTimeMillis()
        val result = action()
        val end = System.currentTimeMillis()

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

    fun <T> blockEnsureMinExecutionTime(
        delayTime: Int = 20,
        action: () -> T,
    ): T {
        val start = System.currentTimeMillis()
        val result = action()
        val end = System.currentTimeMillis()

        val remainingDelay = delayTime + start - end

        if (remainingDelay > 0) {
            Thread.sleep(remainingDelay)
        }
        return result
    }

    fun <T> blockExponentialBackoffUntilValid(
        initTime: Long,
        maxTime: Long,
        isValidResult: (T) -> Boolean,
        action: () -> T,
    ): T {
        var result = action()
        var sum = 0L
        var waiting = initTime
        while (!isValidResult(result) && sum < maxTime) {
            Thread.sleep(waiting)
            sum += waiting
            waiting *= 2
            result = action()
        }
        return result
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
}
