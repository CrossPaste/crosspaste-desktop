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
    companion object {
        private const val DEFAULT_POLL_INTERVAL = 60000L
        private const val RETRY_BASE_DELAY = 500L
        private const val MAX_RETRY_POWER = 10

        // Check every second if we need to execute earlier
        private const val CHECK_INTERVAL = 1000L
    }

    private val logger = KotlinLogging.logger {}

    @Volatile
    private var nextExecutionTime: Long = 0L

    @Volatile
    private var failTime = 0

    // Version number to track state changes
    @Volatile
    private var stateVersion = 0

    fun reset() {
        failTime = 0
        stateVersion++
        // Update next execution time immediately
        updateNextExecutionTime()
    }

    fun fail() {
        failTime++
        stateVersion++
        // Recalculate time immediately on failure
        updateNextExecutionTime()
    }

    private fun updateNextExecutionTime() {
        val delayTime =
            if (failTime > 0) {
                val power = min(MAX_RETRY_POWER, failTime)
                RETRY_BASE_DELAY + min(RETRY_BASE_DELAY * (1L shl power), DEFAULT_POLL_INTERVAL)
            } else {
                DEFAULT_POLL_INTERVAL
            }

        val newTime = nowEpochMilliseconds() + delayTime

        nextExecutionTime = newTime
    }

    fun startPollingResolve(action: suspend () -> Unit): Job =
        syncHandlerScope.launch {
            while (isActive) {
                runCatching {
                    waitForNextExecution()
                    action()
                }.onFailure { e ->
                    if (e !is CancellationException) {
                        logger.error(e) { "polling error" }
                    }
                }
            }
        }

    private suspend fun waitForNextExecution() {
        if (nextExecutionTime <= nowEpochMilliseconds()) {
            updateNextExecutionTime()
        }

        while (nextExecutionTime > nowEpochMilliseconds()) {
            // Use shorter check intervals to respond to state changes
            val waitTime =
                min(
                    CHECK_INTERVAL,
                    nextExecutionTime - nowEpochMilliseconds(),
                )
            if (waitTime > 0) {
                delay(waitTime)
            }
        }
    }
}
