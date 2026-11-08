package com.crosspaste.notification

import com.crosspaste.presist.OneFilePersist
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationCenterTest {
    @TempDir
    lateinit var directory: Path

    private val historyFile get() = directory.resolve("notifications.json")
    private val backupFile get() = directory.resolve("notifications.json.corrupt")

    private fun center() = NotificationCenter(OneFilePersist(historyFile.toOkioPath()))

    @Test
    fun `saved notifications reload newest first with their details`() =
        runTest {
            val center = center()
            center.save("first").getOrThrow()
            center.save("second", "details", MessageType.Warning).getOrThrow()

            val reopened = center()
            reopened.load().getOrThrow()

            val entries = assertNotNull(reopened.notifications.value)
            assertEquals(center.notifications.value, entries)
            assertEquals(listOf("second", "first"), entries.map { it.titleKey })
            assertEquals("details", entries.first().messageKey)
            assertEquals(MessageType.Warning, entries.first().messageType)
        }

    @Test
    fun `clearing persists and does not reset a delivered once key`() =
        runTest {
            val center = center()
            assertTrue(center.save("first", onceKey = "once").getOrThrow())
            center.clearAll().getOrThrow()

            val reopened = center()
            reopened.load().getOrThrow()
            assertEquals(emptyList(), reopened.notifications.value)
            assertFalse(reopened.save("first", onceKey = "once").getOrThrow())
            assertTrue(reopened.save("other", onceKey = "another").getOrThrow())
            val entries = assertNotNull(reopened.notifications.value)
            assertEquals(listOf("other"), entries.map { it.titleKey })
        }

    @Test
    fun `concurrent deliveries for one key save only one notification`() =
        runTest {
            val center = center()
            val deliveries =
                List(8) {
                    async { center.save("first", onceKey = "once").getOrThrow() }
                }.awaitAll()

            assertEquals(1, deliveries.count { it })
            val entries = assertNotNull(center.notifications.value)
            assertEquals(1, entries.size)
        }

    @Test
    fun `a failed write does not mark a notice as delivered`() =
        runTest {
            val center = center()
            center.load().getOrThrow()
            val blockedPath = Files.createDirectory(historyFile)

            assertTrue(center.save("first", onceKey = "once").isFailure)
            assertEquals(emptyList(), center.notifications.value)

            Files.delete(blockedPath)
            assertTrue(center.save("first", onceKey = "once").getOrThrow())
        }

    @Test
    fun `unreadable schema is backed up before accepting new notices`() =
        runTest {
            val corrupt = """{"notifications":[{"id":"legacy"}]}"""
            Files.writeString(historyFile, corrupt)
            val center = center()

            center.load().getOrThrow()
            assertEquals(emptyList(), center.notifications.value)
            assertEquals(corrupt, Files.readString(backupFile))
            assertFalse(Files.exists(historyFile))

            assertTrue(center.save("recovered", onceKey = "new-notice").getOrThrow())
            val reopened = center()
            reopened.load().getOrThrow()
            val entries = assertNotNull(reopened.notifications.value)
            assertEquals("recovered", entries.single().titleKey)
            assertEquals(corrupt, Files.readString(backupFile))
        }

    @Test
    fun `backup failure leaves corrupt history intact and can be retried`() =
        runTest {
            val corrupt = "{"
            Files.writeString(historyFile, corrupt)
            Files.createDirectory(backupFile)
            val blocker = Files.createFile(backupFile.resolve("existing"))
            val center = center()

            assertTrue(center.load().isFailure)
            assertTrue(center.save("pending", onceKey = "new-notice").isFailure)
            assertEquals(corrupt, Files.readString(historyFile))
            assertNull(center.notifications.value)

            Files.delete(blocker)
            Files.delete(backupFile)
            center.load().getOrThrow()
            assertEquals(corrupt, Files.readString(backupFile))
            assertTrue(center.save("pending", onceKey = "new-notice").getOrThrow())
        }

    @Test
    fun `evicting old notices keeps their delivery keys after restart`() =
        runTest {
            val center = center()
            assertTrue(center.save("old", onceKey = "once").getOrThrow())
            repeat(100) { index -> center.save("entry-$index").getOrThrow() }

            val reopened = center()
            reopened.load().getOrThrow()
            val entries = assertNotNull(reopened.notifications.value)
            assertEquals((99 downTo 0).map { "entry-$it" }, entries.map { it.titleKey })
            assertFalse(reopened.save("old", onceKey = "once").getOrThrow())
        }
}
