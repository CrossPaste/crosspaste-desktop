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
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * Integration tier: the action runs on the real main and IO dispatchers, so the
 * held-key wait is observed on wall-clock time.
 */
@IntegrationTest
class DesktopShortKeysActionTest {

    // Initialize JsonUtils before mocking a PasteItem subclass (circular class init)
    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private fun keyEvent(
        id: Int,
        keyCode: Int,
    ) = NativeKeyEvent(id, 0, 0, keyCode, NativeKeyEvent.CHAR_UNDEFINED)

    @Test
    fun `shortcut paste waits until the shortcut keys are released`() {
        val platform = Platform(name = Platform.MACOS, arch = "aarch64", bitMode = 64, version = "26")
        val shortcutKeys = mockk<ShortcutKeys>()
        every { shortcutKeys.shortcutKeysCore } returns MutableStateFlow(ShortcutKeysCore({}, emptyMap()))
        val listener = DesktopShortcutKeysListener(platform, shortcutKeys)

        val pasteText = mockk<TextPasteItem>(relaxed = true)
        val pasteData = mockk<PasteData>(relaxed = true)
        every { pasteData.getPasteAppearItems() } returns listOf<PasteItem>(pasteText)
        val currentPaste = mockk<CurrentPaste>()
        coEvery { currentPaste.getCurrentPaste() } returns pasteData
        val pasteboardService = mockk<PasteboardService>()
        coEvery {
            pasteboardService.tryWritePasteboard(
                id = any(),
                pasteItem = any(),
                localOnly = any(),
                updateCreateTime = any(),
            )
        } returns Result.success(Unit)
        val appWindowManager = mockk<DesktopAppWindowManager>(relaxed = true)

        val action =
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

        runBlocking {
            // The user is still holding Ctrl+Shift when the shortcut fires.
            listener.nativeKeyPressed(keyEvent(NativeKeyEvent.NATIVE_KEY_PRESSED, NativeKeyEvent.VC_CONTROL))
            listener.nativeKeyPressed(keyEvent(NativeKeyEvent.NATIVE_KEY_PRESSED, NativeKeyEvent.VC_SHIFT))
            action.action(DesktopShortcutKeys.PASTE_PLAIN_TEXT, keyEvent(NativeKeyEvent.NATIVE_KEY_PRESSED, 0))

            delay(200.milliseconds)
            coVerify(exactly = 0) { appWindowManager.toPaste() }

            listener.nativeKeyReleased(keyEvent(NativeKeyEvent.NATIVE_KEY_RELEASED, NativeKeyEvent.VC_SHIFT))
            listener.nativeKeyReleased(keyEvent(NativeKeyEvent.NATIVE_KEY_RELEASED, NativeKeyEvent.VC_CONTROL))

            coVerify(timeout = 1000) { appWindowManager.toPaste() }
        }
    }
}
