package com.crosspaste.utils

import com.crosspaste.utils.DateUtils.nowEpochMilliseconds

actual fun getControlUtils(): ControlUtils = DesktopControlUtils

object DesktopControlUtils : ControlUtils {

    fun <T> blockEnsureMinExecutionTime(
        delayTime: Int = 20,
        action: () -> T,
    ): T {
        val start = nowEpochMilliseconds()
        val result = action()
        val end = nowEpochMilliseconds()

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
