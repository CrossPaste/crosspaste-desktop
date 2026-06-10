package com.crosspaste.utils

import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

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

    /**
     * Retries [action] with exponentially growing waits (initTime, 2x, 4x, ...)
     * until [isValidResult] passes or [maxTime] ms of real elapsed time is spent.
     * Suits cases where readiness is usually immediate and rarely late.
     */
    suspend fun <T> exponentialBackoffUntilValid(
        initTime: Long,
        maxTime: Long,
        isValidResult: (T) -> Boolean,
        action: suspend () -> T,
    ): T = backoffUntilValid(initTime, maxTime, { it * 2 }, isValidResult, action)

    /**
     * Retries [action] with linearly growing waits (initTime, 2x, 3x, ...)
     * until [isValidResult] passes or [maxTime] ms of real elapsed time is spent.
     * Keeps late-readiness detection latency low where exponential gaps would
     * overshoot (e.g. a clipboard owner that becomes readable after a few
     * hundred ms).
     */
    suspend fun <T> linearBackoffUntilValid(
        initTime: Long,
        maxTime: Long,
        isValidResult: (T) -> Boolean,
        action: suspend () -> T,
    ): T = backoffUntilValid(initTime, maxTime, { it + initTime }, isValidResult, action)
}

/**
 * Shared retry loop: the budget is real elapsed time (action time included),
 * and the final wait is clamped so the total never overshoots [maxTime].
 */
private suspend fun <T> backoffUntilValid(
    initTime: Long,
    maxTime: Long,
    nextWaiting: (Long) -> Long,
    isValidResult: (T) -> Boolean,
    action: suspend () -> T,
): T {
    val start = nowEpochMilliseconds()
    var result = action()
    var waiting = initTime
    while (!isValidResult(result)) {
        val remaining = maxTime - (nowEpochMilliseconds() - start)
        if (remaining <= 0) {
            break
        }
        delay(minOf(waiting, remaining).milliseconds)
        waiting = nextWaiting(waiting)
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
