package com.crosspaste.utils

import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import io.github.oshai.kotlinlogging.KLogger

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
