package com.crosspaste.pairing.v3

import com.crosspaste.utils.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Explicit pairing-acceptance window on the acceptor.
 *
 * Incoming v3 intents are only accepted while this window is open — typically while
 * the user has the Add Device screen visible. This prevents arbitrary local-network
 * clients from continuously creating PIN cards or exhausting session capacity.
 */
class PairingAcceptanceWindow(
    private val nowEpochMillis: () -> Long = { DateUtils.nowEpochMilliseconds() },
) {

    private val _openUntil = MutableStateFlow(0L)

    /** Epoch millis until which the window is open; 0 when closed. UI observes this. */
    val openUntil: StateFlow<Long> = _openUntil.asStateFlow()

    fun open(duration: Duration = DEFAULT_WINDOW_DURATION) {
        _openUntil.value = nowEpochMillis() + duration.inWholeMilliseconds
    }

    fun close() {
        _openUntil.value = 0L
    }

    fun isOpen(): Boolean = nowEpochMillis() < _openUntil.value

    companion object {
        val DEFAULT_WINDOW_DURATION: Duration = 5.minutes
    }
}
