package com.crosspaste.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * Polls [condition] every [pollInterval] until it returns true or [timeout] elapses.
 * Returns whether the condition was met.
 */
suspend fun pollUntil(
    timeout: Duration,
    pollInterval: Duration,
    condition: () -> Boolean,
): Boolean =
    withTimeoutOrNull(timeout) {
        while (!condition()) {
            delay(pollInterval)
        }
        true
    } ?: false
