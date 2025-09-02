package com.crosspaste.sync

import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

class SyncPollingManager(
    private val syncHandlerScope: CoroutineScope,
) {

    private val logger = KotlinLogging.logger {}

    private var recommendedRefreshTime: Long = 0L

    private var failTime = 0

    fun reset() {
        failTime = 0
    }

    fun fail() {
        failTime++
    }

    fun startPollingResolve(action: suspend () -> Unit): Job =
        syncHandlerScope.launch {
            while (isActive) {
                runCatching {
                    if (recommendedRefreshTime > nowEpochMilliseconds()) {
                        waitNext()
                    }
                    action()
                }.onFailure { e ->
                    if (e !is CancellationException) {
                        logger.error(e) { "polling error" }
                    }
                }
            }
        }

    private suspend fun waitNext() {
        if (recommendedRefreshTime <= nowEpochMilliseconds()) {
            recommendedRefreshTime = computeRefreshTime()
        }

        do {
            // if recommendedRefreshTime is updated, then we continue to wait for the new time
            val waitTime = recommendedRefreshTime - nowEpochMilliseconds()
            delay(waitTime)
        } while (waitTime > 0)
    }

    private fun computeRefreshTime(): Long {
        var delayTime = 60000L // wait 1 min by default
        if (failTime > 0) {
            val power = min(11, failTime)
            delayTime = 1000 + min(20L * (1L shl power), 59000L)
        }
        return nowEpochMilliseconds() + delayTime
    }
}
