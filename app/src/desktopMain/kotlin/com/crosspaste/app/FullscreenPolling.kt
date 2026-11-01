package com.crosspaste.app

import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration

/**
 * Emits [isFullscreen] at once and then every [interval], skipping repeats. A failing probe
 * counts as not full screen and is logged only on its first failure, not on every tick.
 */
internal fun pollFullscreen(
    interval: Duration,
    logger: KLogger,
    isFullscreen: () -> Boolean,
): Flow<Boolean> =
    flow {
        var warned = false
        while (true) {
            val fullscreen =
                runCatching(isFullscreen).getOrElse { e ->
                    if (!warned) {
                        warned = true
                        logger.warn(e) { "Full-screen probe failed, the paste panel button stays shown" }
                    }
                    false
                }
            emit(fullscreen)
            delay(interval)
        }
    }.distinctUntilChanged()
