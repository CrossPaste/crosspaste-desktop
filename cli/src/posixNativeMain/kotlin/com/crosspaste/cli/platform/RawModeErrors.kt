package com.crosspaste.cli.platform

/**
 * POSIX Mordant honors the readKeyOrNull null-on-timeout contract, so no
 * RuntimeException from the key poll is ever a timeout here — every one is a
 * real error and must propagate (see the mingw actual for the Windows bug).
 */
fun isRawModeReadTimeout(
    @Suppress("UNUSED_PARAMETER") e: RuntimeException,
): Boolean = false
