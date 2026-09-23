package com.crosspaste.config

import com.crosspaste.utils.getPlatformUtils
import io.mockk.every
import io.mockk.mockk
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LargeFileDestinationTest {

    private fun config(largeFileDestinationPath: String): AppConfig =
        mockk<AppConfig>(relaxed = true).also {
            every { it.largeFileDestinationPath } returns largeFileDestinationPath
        }

    @Test
    fun `empty config resolves to the system Downloads folder`() {
        assertEquals(
            getPlatformUtils().getSystemDownloadDir(),
            config("").resolveLargeFileDestination(),
        )
    }

    @Test
    fun `configured path resolves to that path`(
        @TempDir tempDir: File,
    ) {
        val destination = File(tempDir, "big-files").also { it.mkdirs() }

        assertEquals(
            destination.toOkioPath(),
            config(destination.absolutePath).resolveLargeFileDestination(),
        )
    }

    @Test
    fun `receive falls back to Downloads when the configured directory is gone`(
        @TempDir tempDir: File,
    ) {
        val removed = File(tempDir, "unplugged-volume")

        assertEquals(
            getPlatformUtils().getSystemDownloadDir(),
            config(removed.absolutePath).resolveLargeFileDestinationForReceive(),
        )
    }

    @Test
    fun `receive falls back to Downloads when the configured path is a file`(
        @TempDir tempDir: File,
    ) {
        val notADirectory = File(tempDir, "note.txt").also { it.writeText("x") }

        assertEquals(
            getPlatformUtils().getSystemDownloadDir(),
            config(notADirectory.absolutePath).resolveLargeFileDestinationForReceive(),
        )
    }

    @Test
    fun `receive keeps a directory that still exists`(
        @TempDir tempDir: File,
    ) {
        val destination = File(tempDir, "big-files").also { it.mkdirs() }

        assertEquals(
            destination.toOkioPath(),
            config(destination.absolutePath).resolveLargeFileDestinationForReceive(),
        )
    }

    @Test
    fun `validate accepts a writable non-empty directory`(
        @TempDir tempDir: File,
    ) {
        val storage = File(tempDir, "storage").also { it.mkdirs() }
        val destination =
            File(tempDir, "big-files").also {
                it.mkdirs()
                File(it, "already-here.txt").writeText("x")
            }

        assertNull(
            validateLargeFileDestination(destination.toOkioPath(), storage.toOkioPath()),
        )
    }

    @Test
    fun `validate rejects a missing directory`(
        @TempDir tempDir: File,
    ) {
        val storage = File(tempDir, "storage").also { it.mkdirs() }

        assertEquals(
            "directory_not_exist",
            validateLargeFileDestination(
                File(tempDir, "nope").toOkioPath(),
                storage.toOkioPath(),
            ),
        )
    }

    @Test
    fun `validate rejects a file`(
        @TempDir tempDir: File,
    ) {
        val storage = File(tempDir, "storage").also { it.mkdirs() }
        val notADirectory = File(tempDir, "note.txt").also { it.writeText("x") }

        assertEquals(
            "not_a_directory",
            validateLargeFileDestination(notADirectory.toOkioPath(), storage.toOkioPath()),
        )
    }

    @Test
    fun `validate rejects managed storage itself`(
        @TempDir tempDir: File,
    ) {
        val storage = File(tempDir, "storage").also { it.mkdirs() }

        assertEquals(
            "cant_select_child_directory",
            validateLargeFileDestination(storage.toOkioPath(), storage.toOkioPath()),
        )
    }

    @Test
    fun `validate rejects a directory inside managed storage`(
        @TempDir tempDir: File,
    ) {
        val storage = File(tempDir, "storage").also { it.mkdirs() }
        val inside = File(storage, "files/received").also { it.mkdirs() }

        assertEquals(
            "cant_select_child_directory",
            validateLargeFileDestination(inside.toOkioPath(), storage.toOkioPath()),
        )
    }

    @Test
    fun `validate rejects a differently cased spelling of managed storage`(
        @TempDir tempDir: File,
    ) {
        val storage = File(tempDir, "Storage").also { it.mkdirs() }
        val inside = File(storage, "Files").also { it.mkdirs() }
        val recased = File(storage.parentFile, "storage/files")

        assertEquals(
            "cant_select_child_directory",
            validateLargeFileDestination(inside.toOkioPath(), recased.toOkioPath()),
        )
    }

    @Test
    fun `validate accepts a sibling of managed storage sharing a name prefix`(
        @TempDir tempDir: File,
    ) {
        val storage = File(tempDir, "storage").also { it.mkdirs() }
        val sibling = File(tempDir, "storage-big-files").also { it.mkdirs() }

        assertNull(
            validateLargeFileDestination(sibling.toOkioPath(), storage.toOkioPath()),
        )
    }
}
