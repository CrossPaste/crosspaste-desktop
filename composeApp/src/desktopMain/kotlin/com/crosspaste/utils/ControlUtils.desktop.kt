package com.crosspaste.utils

import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong

actual fun getControlUtils(): ControlUtils {
    return DesktopControlUtils
}

object DesktopControlUtils : ControlUtils {

    override suspend fun <T> ensureMinExecutionTime(
        delayTime: Int,
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

    override suspend fun <T> exponentialBackoffUntilValid(
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

    override fun <T> blockEnsureMinExecutionTime(
        delayTime: Int,
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

    override fun <T> blockExponentialBackoffUntilValid(
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

    private fun createDebouncedCheck(delay: Long): () -> Boolean {
        val lastExecutionTime = AtomicLong(0)
        return {
            val currentTime = System.currentTimeMillis()
            val previousTime = lastExecutionTime.get()
            if (currentTime - previousTime > delay || previousTime == 0L) {
                lastExecutionTime.set(currentTime)
                true
            } else {
                false
            }
        }
    }

    override fun blockDebounce(
        delay: Long,
        action: () -> Unit,
    ): () -> Unit {
        val shouldExecute = createDebouncedCheck(delay)
        return {
            if (shouldExecute()) {
                action()
            }
        }
    }

    override fun debounce(
        delay: Long,
        action: suspend () -> Unit,
    ): suspend () -> Unit {
        val shouldExecute = createDebouncedCheck(delay)
        return {
            if (shouldExecute()) {
                action()
            }
        }
    }

    override fun <T> debounce(
        delay: Long,
        action: suspend (T) -> Unit,
    ): suspend (T) -> Unit {
        val long = AtomicLong(0)
        return { key ->
            val currentTime = System.currentTimeMillis()
            val previousTime = long.get()
            if (currentTime - previousTime > delay || previousTime == 0L) {
                long.set(currentTime)
                action(key)
            }
        }
    }
}
