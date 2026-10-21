package com.crosspaste.app

import androidx.compose.ui.window.WindowState
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.listener.ShortcutKeysCore
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LinuxPasteTargetTest {
    private val notifications = mockk<NotificationManager>(relaxed = true)

    private fun createManager(): LinuxAppWindowManager {
        val appSize =
            mockk<DesktopAppSize> {
                every { getMainWindowState() } returns WindowState()
                every { getSearchWindowState(any()) } returns WindowState()
            }
        val shortcutKeys =
            mockk<ShortcutKeys> {
                every { shortcutKeysCore } returns MutableStateFlow(ShortcutKeysCore({}, emptyMap()))
            }
        val manager =
            spyk(
                LinuxAppWindowManager(
                    appInfo = mockk(),
                    appSize = appSize,
                    lazyConfigManager = lazy { mockk() },
                    lazyShortcutKeys = lazy { shortcutKeys },
                    lazyShortcutKeysAction = lazy { mockk() },
                    lazyShortcutKeysListener = lazy { mockk() },
                    lazyNotificationManager = lazy { notifications },
                    userDataPathProvider = mockk(),
                ),
                recordPrivateCalls = true,
            )
        // Exercise the X11 control flow on every host without loading native X11.
        every { manager["needsPortalPaste"]() } returns false
        coEvery { manager.toPaste() } returns Unit
        manager.showSearchWindow(WindowTrigger.SHORTCUT)
        return manager
    }

    @Test
    fun `missing previous window stops before preparing or pasting the remaining items`() =
        runTest {
            val manager = createManager()
            val prepared = mutableListOf<Int>()

            manager.hideSearchWindowAndPaste(3) {
                prepared.add(it)
                true
            }

            assertEquals(listOf(0), prepared)
            coVerify(exactly = 0) { manager.toPaste() }
            verify(exactly = 1) { notifications.sendNotification(any(), any(), MessageType.Info, any()) }
            assertFalse(manager.getCurrentSearchWindowInfo().show)
        }

    @Test
    fun `failed target activation stops before preparing or pasting the remaining items`() =
        runTest {
            val manager = createManager()
            // Native activation and its fallback both failed. Keep the caller's loop real.
            coEvery { manager["bringToBack"](any<Boolean>(), any<() -> Unit>()) } returns false
            val prepared = mutableListOf<Int>()

            manager.hideSearchWindowAndPaste(3) {
                prepared.add(it)
                true
            }

            assertEquals(listOf(0), prepared)
            coVerify(exactly = 0) { manager.toPaste() }
            assertFalse(manager.getCurrentSearchWindowInfo().show)
        }

    @Test
    fun `ready target still pastes subsequent prepared items in order`() =
        runTest {
            val manager = createManager()
            coEvery { manager["bringToBack"](any<Boolean>(), any<() -> Unit>()) } returns true
            val prepared = mutableListOf<Int>()
            val pasted = mutableListOf<Int>()
            coEvery { manager.toPaste() } answers {
                pasted.add(prepared.last())
                Unit
            }

            manager.hideSearchWindowAndPaste(4) {
                prepared.add(it)
                it != 2
            }

            assertEquals(listOf(0, 1, 2, 3), prepared)
            assertEquals(listOf(1, 3), pasted)
            assertFalse(manager.getCurrentSearchWindowInfo().show)
        }
}
