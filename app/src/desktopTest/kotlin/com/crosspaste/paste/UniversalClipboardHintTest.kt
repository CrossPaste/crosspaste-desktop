package com.crosspaste.paste

import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationCenter
import com.crosspaste.notification.NotificationManager
import com.crosspaste.presist.OneFilePersist
import com.crosspaste.sync.SyncHandler
import com.crosspaste.sync.SyncManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UniversalClipboardHintTest {

    @TempDir
    lateinit var directory: Path

    private val notificationManager = mockk<NotificationManager>(relaxed = true)

    private fun center() = NotificationCenter(OneFilePersist(directory.resolve("notifications.json").toOkioPath()))

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

    private fun verifyHintToasted(times: Int) {
        verify(exactly = times) {
            notificationManager.sendNotification(any(), any(), MessageType.Info, any())
        }
    }

    @Test
    fun `the hint is saved and toasted once when an Apple device is paired`() =
        runTest {
            val center = center()
            val hint = UniversalClipboardHint(center, notificationManager, syncManagerWith(false, true))

            hint.onUniversalClipboardContent()
            hint.onUniversalClipboardContent()

            verifyHintToasted(1)
            val entries = assertNotNull(center.notifications.value)
            assertEquals(listOf("universal_clipboard_hint_title"), entries.map { it.titleKey })
        }

    @Test
    fun `the hint stays quiet while no Apple device is paired`() =
        runTest {
            val center = center()
            val hint = UniversalClipboardHint(center, notificationManager, syncManagerWith(false))

            hint.onUniversalClipboardContent()

            verifyHintToasted(0)
            center.load().getOrThrow()
            assertEquals(emptyList(), assertNotNull(center.notifications.value))
        }

    @Test
    fun `a cleared hint does not come back after a restart`() =
        runTest {
            val center = center()
            UniversalClipboardHint(center, notificationManager, syncManagerWith(true)).onUniversalClipboardContent()
            center.clearAll().getOrThrow()

            val reopened = center()
            reopened.load().getOrThrow()
            UniversalClipboardHint(reopened, notificationManager, syncManagerWith(true)).onUniversalClipboardContent()

            // Only the first instance toasted; the reopened one saw the delivery key
            // that survived clearing, so it neither saved nor toasted.
            verifyHintToasted(1)
            assertEquals(emptyList(), assertNotNull(reopened.notifications.value))
        }
}
