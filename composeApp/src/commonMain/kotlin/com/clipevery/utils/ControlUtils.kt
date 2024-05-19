package com.clipevery.utils

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
