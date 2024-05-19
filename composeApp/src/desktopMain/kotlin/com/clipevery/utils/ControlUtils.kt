package com.clipevery.utils

import kotlinx.coroutines.delay

object ControlUtils {

    suspend fun <T> ensureMinExecutionTime(
        delayTime: Int = 20,
        action: () -> T,
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
        action: () -> T,
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
}
