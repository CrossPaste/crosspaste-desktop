package com.crosspaste.net.routing

import com.crosspaste.presist.FileTransferResourceLimits
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PushIconWriteTest {

    @TempDir
    lateinit var tempDir: File

    private fun partFiles(): List<File> = tempDir.listFiles().orEmpty().filter { it.name.endsWith(".part") }

    @Test
    fun concurrentUploadsForSameSourceDoNotCorruptTheIcon() =
        runBlocking {
            val iconPath = (tempDir.toOkioPath()) / "TestApp.png"
            val payloadA = ByteArray(100_000) { 0xAA.toByte() }
            val payloadB = ByteArray(100_000) { 0xBB.toByte() }

            val results =
                listOf(payloadA, payloadB)
                    .map { payload ->
                        async { writeIconAtomically(iconPath, ByteReadChannel(payload)) }
                    }.awaitAll()

            assertTrue(results.any { it }, "at least one upload must win")
            val stored = iconPath.toFile().readBytes()
            assertTrue(
                stored.contentEquals(payloadA) || stored.contentEquals(payloadB),
                "stored icon must be exactly one complete payload",
            )
            assertEquals(emptyList(), partFiles(), "no temp files may remain")
        }

    @Test
    fun oversizedUploadKeepsExistingIconIntact() =
        runBlocking {
            val iconPath = (tempDir.toOkioPath()) / "TestApp.png"
            val existingIcon = ByteArray(1_000) { 0x11 }
            iconPath.toFile().writeBytes(existingIcon)

            val oversized = ByteArray((FileTransferResourceLimits.MAX_ICON_SIZE + 1).toInt())
            val result = writeIconAtomically(iconPath, ByteReadChannel(oversized))

            assertFalse(result, "oversized upload must fail")
            assertContentEquals(existingIcon, iconPath.toFile().readBytes())
            assertEquals(emptyList(), partFiles(), "no temp files may remain")
        }
}
