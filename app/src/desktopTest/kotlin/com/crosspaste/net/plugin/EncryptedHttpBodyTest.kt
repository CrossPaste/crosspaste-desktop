package com.crosspaste.net.plugin

import com.crosspaste.presist.FileTransferResourceLimits
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.test.runTest
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EncryptedHttpBodyTest {

    @Test
    fun `request limits follow route payload types`() {
        val controlLimit = EncryptedHttpResourceLimits.requestCiphertextLimit("/sync/heartbeat")
        val iconLimit = EncryptedHttpResourceLimits.requestCiphertextLimit("/sync/icon/push/source")
        val chunkLimit = EncryptedHttpResourceLimits.requestCiphertextLimit("/sync/file/push")
        val pasteLimit = EncryptedHttpResourceLimits.requestCiphertextLimit("/sync/paste")

        assertTrueAscending(controlLimit, iconLimit, chunkLimit, pasteLimit)
        assertEquals(controlLimit, EncryptedHttpResourceLimits.requestCiphertextLimit("/pull/file"))
        assertEquals(
            FileTransferResourceLimits.MAX_CHUNK_SIZE + 32,
            chunkLimit,
        )
    }

    @Test
    fun `content length rejects oversized fixed request before reading`() {
        validateEncryptedContentLength(3, 3, "test body")
        assertFailsWith<EncryptedPayloadException> {
            validateEncryptedContentLength(4, 3, "test body")
        }
    }

    @Test
    fun `encrypted payload classifier unwraps transport exceptions`() {
        val wrapped = IllegalStateException("channel failed", EncryptedPayloadException("too large"))

        assertTrue(wrapped.isEncryptedPayloadException())
        assertFalse(IllegalStateException("other").isEncryptedPayloadException())
    }

    @Test
    fun `limited channel read bounds chunked request at actual bytes`() =
        runTest {
            assertContentEquals(
                byteArrayOf(1, 2, 3),
                ByteReadChannel(byteArrayOf(1, 2, 3)).readLimitedByteArray(3, "test body"),
            )

            assertFailsWith<EncryptedPayloadException> {
                ByteReadChannel(byteArrayOf(1, 2, 3, 4)).readLimitedByteArray(3, "test body")
            }
        }

    @Test
    fun `JSON response checks ciphertext and decrypted limits`() =
        runTest {
            assertFailsWith<EncryptedPayloadException> {
                decryptJsonResponse(
                    channel = ByteReadChannel(byteArrayOf(1, 2, 3, 4)),
                    maxCiphertextBytes = 3,
                    maxPlaintextBytes = 10,
                    decrypt = { it },
                )
            }

            assertFailsWith<EncryptedPayloadException> {
                decryptJsonResponse(
                    channel = ByteReadChannel(byteArrayOf(1)),
                    maxCiphertextBytes = 3,
                    maxPlaintextBytes = 3,
                    decrypt = { ByteArray(4) },
                )
            }
        }

    @Test
    fun `chunked response rejects invalid record size before allocation`() =
        runTest {
            assertFailsWith<EncryptedPayloadException> {
                decryptChunkedResponse(
                    channel = ByteReadChannel(framePrefix(-1)),
                    maxRecordCiphertextBytes = 8,
                    maxPlaintextBytes = 8,
                    decrypt = { it },
                )
            }

            assertFailsWith<EncryptedPayloadException> {
                decryptChunkedResponse(
                    channel = ByteReadChannel(framePrefix(9)),
                    maxRecordCiphertextBytes = 8,
                    maxPlaintextBytes = 8,
                    decrypt = { it },
                )
            }
        }

    @Test
    fun `chunked response rejects partial length prefix`() =
        runTest {
            assertFailsWith<EncryptedPayloadException> {
                decryptChunkedResponse(
                    channel = ByteReadChannel(byteArrayOf(0, 0, 0)),
                    maxRecordCiphertextBytes = 8,
                    maxPlaintextBytes = 8,
                    decrypt = { it },
                )
            }
        }

    @Test
    fun `chunked response enforces cumulative plaintext limit`() =
        runTest {
            val channel = ByteReadChannel(chunked(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6)))

            assertFailsWith<EncryptedPayloadException> {
                decryptChunkedResponse(
                    channel = channel,
                    maxRecordCiphertextBytes = 8,
                    maxPlaintextBytes = 5,
                    decrypt = { it },
                )
            }
        }

    @Test
    fun `chunked response preserves valid records`() =
        runTest {
            val result =
                decryptChunkedResponse(
                    channel = ByteReadChannel(chunked(byteArrayOf(1, 2), byteArrayOf(3, 4))),
                    maxRecordCiphertextBytes = 2,
                    maxPlaintextBytes = 4,
                    decrypt = { it },
                )

            assertContentEquals(byteArrayOf(1, 2, 3, 4), result)
        }

    private fun framePrefix(size: Int): ByteArray =
        buildPacket {
            writeInt(size)
        }.readByteArray()

    private fun chunked(vararg records: ByteArray): ByteArray =
        buildPacket {
            records.forEach { record ->
                writeInt(record.size)
                writeFully(record)
            }
        }.readByteArray()

    private fun assertTrueAscending(vararg values: Long) {
        for (index in 0 until values.lastIndex) {
            assertTrue(
                values[index] < values[index + 1],
                "${values[index]} must be smaller than ${values[index + 1]}",
            )
        }
    }
}
