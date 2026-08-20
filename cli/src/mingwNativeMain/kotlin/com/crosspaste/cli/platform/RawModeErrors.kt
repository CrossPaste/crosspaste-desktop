package com.crosspaste.cli.platform

/**
 * Mordant 3.0.2's native-Windows readRawEvent throws a plain
 * RuntimeException with exactly this message when the key-poll wait expires,
 * instead of returning null like every other platform (its common wrapper
 * only swallows TimeoutException). Only this known signature may be treated
 * as "no key this tick"; anything else is a real console error and must
 * propagate.
 */
private const val MORDANT_WINDOWS_TIMEOUT_MESSAGE = "Timeout reading from console input"

fun isRawModeReadTimeout(e: RuntimeException): Boolean = e.message == MORDANT_WINDOWS_TIMEOUT_MESSAGE
