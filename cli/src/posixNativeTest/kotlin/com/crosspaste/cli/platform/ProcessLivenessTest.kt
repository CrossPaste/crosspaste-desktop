package com.crosspaste.cli.platform

import platform.posix.getpid
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProcessLivenessTest {

    @Test
    fun ownProcessIsAlive() {
        assertTrue(isProcessAlive(getpid().toLong()))
    }

    @Test
    fun initProcessCountsAsAliveDespiteEperm() {
        // pid 1 exists on every POSIX system; kill(1, 0) fails with EPERM for
        // unprivileged callers, which must still be reported as alive
        assertTrue(isProcessAlive(1))
    }

    @Test
    fun invalidPidsAreDead() {
        assertFalse(isProcessAlive(0))
        assertFalse(isProcessAlive(-1))
    }
}
