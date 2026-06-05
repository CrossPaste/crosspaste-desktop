package com.crosspaste.net

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class NoopNetworkStateMonitorTest {

    @Test
    fun `never emits so discovery stays config-driven`() =
        runTest {
            val monitor = NoopNetworkStateMonitor()
            monitor.start()
            // emptyFlow completes immediately without emitting.
            val emissions = monitor.networkChanges.toList()
            monitor.stop()
            assertTrue(emissions.isEmpty())
        }
}
