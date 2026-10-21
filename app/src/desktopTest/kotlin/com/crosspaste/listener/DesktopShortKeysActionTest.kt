package com.crosspaste.listener

import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.paste.CurrentPaste
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.platform.Platform
import com.crosspaste.test.IntegrationTest
import com.crosspaste.utils.getJsonUtils
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Integration tier: the action runs on the real main and IO dispatchers, so the
 * held-key wait is observed on wall-clock time.
 */
@IntegrationTest
class DesktopShortKeysActionTest {

    private companion object {
        const val VERIFY_TIMEOUT_MS = 5_000L
    }

    // Initialize JsonUtils before mocking a PasteItem subclass (circular class init)
    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private fun keyEvent(
        id: Int,
        keyCode: Int,
    ) = NativeKeyEvent(id, 0, 0, keyCode, NativeKeyEvent.CHAR_UNDEFINED)

    private fun pressControlAndShift(listener: DesktopShortcutKeysListener) {
        listener.nativeKeyPressed(keyEvent(NativeKeyEvent.NATIVE_KEY_PRESSED, NativeKeyEvent.VC_CONTROL))
        listener.nativeKeyPressed(keyEvent(NativeKeyEvent.NATIVE_KEY_PRESSED, NativeKeyEvent.VC_SHIFT))
    }

    private fun releaseControlAndShift(listener: DesktopShortcutKeysListener) {
        listener.nativeKeyReleased(keyEvent(NativeKeyEvent.NATIVE_KEY_RELEASED, NativeKeyEvent.VC_SHIFT))
        listener.nativeKeyReleased(keyEvent(NativeKeyEvent.NATIVE_KEY_RELEASED, NativeKeyEvent.VC_CONTROL))
    }

    private class Fixture {
        private val platform = Platform(name = Platform.MACOS, arch = "aarch64", bitMode = 64, version = "26")

        private val shortcutKeys =
            mockk<ShortcutKeys> {
                every { shortcutKeysCore } returns MutableStateFlow(ShortcutKeysCore({}, emptyMap()))
            }

        val pasteboardService = mockk<PasteboardService>()

        val appWindowManager = mockk<DesktopAppWindowManager>(relaxed = true)

        /** Wall-clock time of the paste keystroke injection, 0 until it happens. */
        val pasteAt = AtomicLong(0L)

        val listener = DesktopShortcutKeysListener(platform, shortcutKeys)

        val action: DesktopShortKeysAction

        init {
            val pasteText = mockk<TextPasteItem>(relaxed = true)
            val pasteData = mockk<PasteData>(relaxed = true)
            every { pasteData.getPasteAppearItems() } returns listOf<PasteItem>(pasteText)
            val currentPaste = mockk<CurrentPaste>()
            coEvery { currentPaste.getCurrentPaste() } returns pasteData
            coEvery {
                pasteboardService.tryWritePasteboard(
                    id = any(),
                    pasteItem = any(),
                    localOnly = any(),
                    updateCreateTime = any(),
                )
            } returns Result.success(Unit)
            coEvery { appWindowManager.toPaste() } answers { pasteAt.set(System.nanoTime()) }

            action =
                DesktopShortKeysAction(
                    appFileChooser = mockk(relaxed = true),
                    appWindowManager = appWindowManager,
                    lazyShortcutKeysListener = lazyOf(listener),
                    configManager = mockk(relaxed = true),
                    currentPaste = currentPaste,
                    notificationManager = mockk(relaxed = true),
                    pasteboardService = pasteboardService,
                    pasteDao = mockk(relaxed = true),
                )
        }
    }

    @Test
    fun `shortcut paste waits until the shortcut keys are released`() {
        val fixture = Fixture()

        runBlocking {
            // The user is still holding Ctrl+Shift when the shortcut fires.
            pressControlAndShift(fixture.listener)
            fixture.action.action(DesktopShortcutKeys.PASTE_PLAIN_TEXT, keyEvent(NativeKeyEvent.NATIVE_KEY_PRESSED, 0))

            // The clipboard write comes right before the held-key wait, so once it has
            // been observed the action is parked in the wait. Waiting for it first keeps
            // the negative check below from passing just because the action has not
            // started yet on a loaded machine.
            coVerify(timeout = VERIFY_TIMEOUT_MS) {
                fixture.pasteboardService.tryWritePasteboard(
                    id = any(),
                    pasteItem = any(),
                    localOnly = any(),
                    updateCreateTime = any(),
                )
            }
            delay(200.milliseconds)
            coVerify(exactly = 0) { fixture.appWindowManager.toPaste() }

            val releasedAt = System.nanoTime()
            releaseControlAndShift(fixture.listener)

            coVerify(timeout = VERIFY_TIMEOUT_MS, exactly = 1) { fixture.appWindowManager.toPaste() }
            assertTrue(
                fixture.pasteAt.get() > releasedAt,
                "the paste must be injected after the shortcut keys are released",
            )
        }
    }

    @Test
    fun `shortcut paste still happens when the held keys are never released`() {
        val fixture = Fixture()

        runBlocking {
            pressControlAndShift(fixture.listener)
            fixture.action.action(DesktopShortcutKeys.PASTE_PLAIN_TEXT, keyEvent(NativeKeyEvent.NATIVE_KEY_PRESSED, 0))

            // The release events are lost: the wait has to time out and let the paste
            // through instead of dropping it.
            coVerify(timeout = VERIFY_TIMEOUT_MS, exactly = 1) { fixture.appWindowManager.toPaste() }
        }
    }
}
