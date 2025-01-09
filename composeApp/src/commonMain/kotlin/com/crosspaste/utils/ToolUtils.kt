package com.crosspaste.utils

import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

class OnceFunction<T>(private val function: () -> T) {
    private var hasRun = false
    private var result: T? = null

    fun run(): T {
        if (!hasRun) {
            result = function()
            hasRun = true
        }
        return result!!
    }
}

object Memoize {
    fun <T, R> memoize(function: (T) -> R): (T) -> R {
        val cache = mutableMapOf<T, R>()
        return {
            cache.getOrPut(it) { function(it) }
        }
    }
}

object Retry {

    val logger = KotlinLogging.logger {}

    fun <T> retry(
        maxAttempts: Int,
        action: () -> T,
        cleanUp: () -> Unit = {},
    ): T {
        var lastException: Exception? = null
        for (attempt in 1..maxAttempts) {
            try {
                return action()
            } catch (e: Exception) {
                lastException = e
                logger.warn(e) { "Attempt $attempt failed, retrying..." }
                cleanUp()
            }
        }
        throw lastException ?: IllegalStateException("Unknown retry failure.")
    }
}

object LoggerExtension {

    fun <T> logExecutionTime(
        logger: KLogger,
        message: String,
        func: () -> T,
    ): T {
        val startTime = nowEpochMilliseconds()
        val result = func()
        val endTime = nowEpochMilliseconds()
        logger.info { "$message Execution Time: ${endTime - startTime} ms" }
        return result
    }

    suspend fun <T> logSuspendExecutionTime(
        logger: KLogger,
        message: String,
        func: suspend () -> T,
    ): T {
        val startTime = nowEpochMilliseconds()
        val result = func()
        val endTime = nowEpochMilliseconds()
        logger.info { "$message Execution Time: ${endTime - startTime} ms" }
        return result
    }
}
