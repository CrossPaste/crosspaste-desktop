package com.crosspaste.app

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration

private val attentiveLogger = KotlinLogging.logger {}

// Exposes per-surface visibility signals so background services can gate work
// on whether the user can actually see the result. Background pollers that
// only surface warnings inside a specific window should subscribe to that
// window's flow via `attentionOn(...)`; pollers that must run regardless of
// UI state (clipboard sync, cleanup) should simply not subscribe.
//
// Bubble window is intentionally not exposed — it's a transient surface that
// no current poller needs to react to. Add it here when a real consumer
// arrives.
interface UserAttentionService {
    val isMainWindowVisible: StateFlow<Boolean>
    val isSearchWindowVisible: StateFlow<Boolean>
}

enum class AttentionSurface {
    MAIN_WINDOW,
    SEARCH_WINDOW,
}

// Returns a flow that emits true whenever *any* of the requested surfaces is
// visible. Distinct-only — re-emits only on transitions.
fun UserAttentionService.attentionOn(vararg surfaces: AttentionSurface): Flow<Boolean> {
    require(surfaces.isNotEmpty()) { "attentionOn requires at least one surface" }
    val flows =
        surfaces.toSet().map { surface ->
            when (surface) {
                AttentionSurface.MAIN_WINDOW -> isMainWindowVisible
                AttentionSurface.SEARCH_WINDOW -> isSearchWindowVisible
            }
        }
    return if (flows.size == 1) {
        // StateFlow already deduplicates, so no distinctUntilChanged here.
        flows.single()
    } else {
        combine(flows) { values -> values.any { it } }.distinctUntilChanged()
    }
}

// Runs `block` on `interval` cadence only while `attention` emits true.
// When attention flips false the inner loop is cancelled; when it flips true
// the loop restarts (firing `block` immediately if `runOnResume` is set, so
// the user sees fresh state the moment they open the relevant surface
// instead of waiting one interval).
//
// Exceptions thrown by `block` are caught and logged so a single bad tick
// doesn't terminate the polling Job and silently stop all future ticks.
// CancellationException is always rethrown to keep structured concurrency
// semantics intact.
fun CoroutineScope.launchWhileAttentive(
    attention: Flow<Boolean>,
    interval: Duration,
    runOnResume: Boolean = true,
    block: suspend () -> Unit,
): Job =
    launch {
        attention.collectLatest { attentive ->
            if (!attentive) return@collectLatest
            if (runOnResume) runTick(block)
            while (true) {
                delay(interval)
                runTick(block)
            }
        }
    }

private suspend fun runTick(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        attentiveLogger.warn(e) { "launchWhileAttentive block threw; continuing on next tick" }
    }
}
