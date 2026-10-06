package com.crosspaste.listener

import com.crosspaste.platform.Platform
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopShortcutKeysListenerTest {

    private fun createListener(): DesktopShortcutKeysListener {
        val platform = Platform(name = Platform.MACOS, arch = "arm64", bitMode = 64, version = "1")
        val shortcutKeys = mockk<ShortcutKeys>()
        val core = ShortcutKeysCore(mockk(relaxed = true), emptyMap())
        every { shortcutKeys.shortcutKeysCore } returns MutableStateFlow(core)
        return DesktopShortcutKeysListener(platform, shortcutKeys)
    }

    private fun createKeyEvent(
        id: Int,
        keyCode: Int,
    ): NativeKeyEvent =
        NativeKeyEvent(
            id,
            0,
            0,
            keyCode,
            NativeKeyEvent.CHAR_UNDEFINED,
            NativeKeyEvent.KEY_LOCATION_STANDARD,
        )

    @Test
    fun `awaitKeysReleased completes after release delivery delay when no keys are held`() =
        runTest {
            val listener = createListener()
            val job = launch { listener.awaitKeysReleased() }

            runCurrent()
            assertFalse(job.isCompleted)

            advanceTimeBy(50.milliseconds)
            runCurrent()
            assertTrue(job.isCompleted)
        }

    @Test
    fun `awaitKeysReleased waits until key is released`() =
        runTest {
            val listener = createListener()
            listener.nativeKeyPressed(createKeyEvent(NativeKeyEvent.NATIVE_KEY_PRESSED, NativeKeyEvent.VC_ESCAPE))

            val job = launch { listener.awaitKeysReleased() }
            runCurrent()
            assertFalse(job.isCompleted)

            advanceTimeBy(200.milliseconds)
            runCurrent()
            assertFalse(job.isCompleted)

            listener.nativeKeyReleased(createKeyEvent(NativeKeyEvent.NATIVE_KEY_RELEASED, NativeKeyEvent.VC_ESCAPE))
            runCurrent()
            assertFalse(job.isCompleted)

            advanceTimeBy(50.milliseconds)
            runCurrent()
            assertTrue(job.isCompleted)
        }

    @Test
    fun `awaitKeysReleased times out and clears held keys when release event is lost`() =
        runTest {
            val listener = createListener()
            listener.nativeKeyPressed(createKeyEvent(NativeKeyEvent.NATIVE_KEY_PRESSED, NativeKeyEvent.VC_ESCAPE))

            val job = launch { listener.awaitKeysReleased(timeout = 200.milliseconds) }
            runCurrent()
            assertFalse(job.isCompleted)

            advanceTimeBy(200.milliseconds)
            runCurrent()
            assertFalse(job.isCompleted) // still in 50ms delivery delay

            advanceTimeBy(50.milliseconds)
            runCurrent()
            assertTrue(job.isCompleted)

            // Subsequent call should not be stuck on the stale key
            val nextJob = launch { listener.awaitKeysReleased(timeout = 200.milliseconds) }
            runCurrent()
            advanceTimeBy(50.milliseconds)
            runCurrent()
            assertTrue(nextJob.isCompleted)
        }
}
