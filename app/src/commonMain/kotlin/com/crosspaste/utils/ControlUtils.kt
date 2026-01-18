package com.crosspaste.utils

import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

expect fun getControlUtils(): ControlUtils

interface ControlUtils {

    suspend fun <T> ensureMinExecutionTime(
        delayTime: Long,
        action: suspend () -> T,
    ): Result<T> {
        val start = nowEpochMilliseconds()
        val result =
            runCatching {
                action()
            }
        val end = nowEpochMilliseconds()

        val remainingDelay = delayTime + start - end

        if (remainingDelay > 0) {
            delay(remainingDelay)
        }
        return result
    }

    suspend fun ensureMinExecutionTimeForCallback(
        delayTime: Long,
        action: (proceed: () -> Unit) -> Unit,
    ): Result<Unit> {
        val start = nowEpochMilliseconds()

        val result =
            runCatching {
                suspendCancellableCoroutine { continuation ->
                    CoroutineScope(continuation.context).launch {
                        action {
                            continuation.resume(Unit)
                        }
                    }
                }
            }

        val end = nowEpochMilliseconds()
        val remainingDelay = delayTime - (end - start)

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
