package com.crosspaste.ui

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.awt.EventQueue
import java.awt.Window
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@EnabledOnOs(OS.WINDOWS)
@OptIn(ExperimentalCoroutinesApi::class)
class WindowReadinessTest {
    @Test
    fun `already visible native window has no startup wait`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val window = mockk<Window>(relaxed = true)
                every { window.isShowing } returns true
                var ready = false
                launch {
                    awaitWindowShowing(window)
                    ready = true
                }
                runCurrent()
                assertTrue(ready)
                assertTrue(testScheduler.currentTime == 0L)
                verify(exactly = 0) { window.addHierarchyListener(any()) }
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `activation starts on actual visibility event without a fixed delay`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val window = mockk<Window>(relaxed = true)
                var showing = false
                every { window.isShowing } answers { showing }
                val listener = slot<HierarchyListener>()
                every { window.addHierarchyListener(capture(listener)) } returns Unit
                var ready = false
                launch {
                    awaitWindowShowing(window)
                    ready = true
                }
                runCurrent()
                assertFalse(ready)
                showing = true
                listener.captured.hierarchyChanged(
                    HierarchyEvent(window, HierarchyEvent.HIERARCHY_CHANGED, window, null),
                )
                runCurrent()
                assertTrue(ready)
                assertTrue(testScheduler.currentTime == 0L)
                verify { window.removeHierarchyListener(listener.captured) }
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `closed appearance cancels readiness listener without activating later`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val window = mockk<Window>(relaxed = true)
                every { window.isShowing } returns false
                val listener = slot<HierarchyListener>()
                every { window.addHierarchyListener(capture(listener)) } returns Unit
                val job =
                    launch {
                        awaitWindowShowing(window)
                        error("Cancelled appearance activated")
                    }
                runCurrent()
                job.cancel()
                runCurrent()
                EventQueue.invokeAndWait { }
                verify { window.removeHierarchyListener(listener.captured) }
            } finally {
                Dispatchers.resetMain()
            }
        }
}
