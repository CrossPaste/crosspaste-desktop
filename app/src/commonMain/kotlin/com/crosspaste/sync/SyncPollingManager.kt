package com.crosspaste.sync

import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val nextExecutionTime = atomic(0L)
    private val failTimeRef = atomic(0)
    private val stateVersionRef = atomic(0)

    private val stateMutex = Mutex()

    suspend fun reset() {
        stateMutex.withLock {
            failTimeRef.value = 0
            stateVersionRef.update { it + 1 }
            // Update next execution time immediately
            updateNextExecutionTimeInternal()
        }
    }

    suspend fun fail() {
        stateMutex.withLock {
            failTimeRef.update { it + 1 }
            stateVersionRef.update { it + 1 }
            // Recalculate time immediately on failure
            updateNextExecutionTimeInternal()
        }
    }

    private fun updateNextExecutionTimeInternal() {
        val currentFailTime = failTimeRef.value
        val delayTime =
            if (currentFailTime > 0) {
                val power = min(MAX_RETRY_POWER, currentFailTime)
                RETRY_BASE_DELAY + min(RETRY_BASE_DELAY * (1L shl power), DEFAULT_POLL_INTERVAL)
            } else {
                DEFAULT_POLL_INTERVAL
            }

        val newTime = nowEpochMilliseconds() + delayTime
        nextExecutionTime.value = newTime
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
        val currentNextExecution = nextExecutionTime.value
        if (currentNextExecution <= nowEpochMilliseconds()) {
            stateMutex.withLock {
                if (nextExecutionTime.value <= nowEpochMilliseconds()) {
                    updateNextExecutionTimeInternal()
                }
            }
        }

        while (nextExecutionTime.value > nowEpochMilliseconds()) {
            // Use shorter check intervals to respond to state changes
            val waitTime =
                min(
                    CHECK_INTERVAL,
                    nextExecutionTime.value - nowEpochMilliseconds(),
                )
            if (waitTime > 0) {
                delay(waitTime)
            }
        }
    }
}
