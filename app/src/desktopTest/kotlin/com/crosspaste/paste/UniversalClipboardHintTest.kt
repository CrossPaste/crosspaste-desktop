package com.crosspaste.paste

import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.presist.OneFilePersist
import com.crosspaste.sync.SyncHandler
import com.crosspaste.sync.SyncManager
import com.crosspaste.utils.DesktopLocaleUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UniversalClipboardHintTest {

    private val notificationManager = mockk<NotificationManager>(relaxed = true)

    private fun configManager(): DesktopConfigManager {
        val dir = Files.createTempDirectory("hintConfig").toOkioPath()
        dir.toFile().deleteOnExit()
        return DesktopConfigManager(OneFilePersist(dir.resolve("appConfig.json")), DesktopLocaleUtils)
    }

    private fun syncManagerWith(vararg apple: Boolean): SyncManager {
        val handlers =
            apple.withIndex().associate { (index, isApple) ->
                "device-$index" to
                    mockk<SyncHandler> {
                        every { currentSyncRuntimeInfo.platform.isApple() } returns isApple
                    }
            }
        return mockk { every { getSyncHandlers() } returns handlers }
    }

    private fun verifyHintSent(times: Int) {
        verify(exactly = times) {
            notificationManager.sendNotification(any(), any(), MessageType.Info, null)
        }
    }

    @Test
    fun `hint is shown once when an Apple device is paired`() {
        val configManager = configManager()
        val hint = UniversalClipboardHint(configManager, notificationManager, syncManagerWith(false, true))

        hint.onUniversalClipboardContent()
        hint.onUniversalClipboardContent()

        verifyHintSent(1)
        assertFalse(configManager.getCurrentConfig().showUniversalClipboardHint)
    }

    @Test
    fun `hint waits while no Apple device is paired`() {
        val configManager = configManager()
        val hint = UniversalClipboardHint(configManager, notificationManager, syncManagerWith(false))

        hint.onUniversalClipboardContent()

        verifyHintSent(0)
        assertTrue(configManager.getCurrentConfig().showUniversalClipboardHint)
    }
}
