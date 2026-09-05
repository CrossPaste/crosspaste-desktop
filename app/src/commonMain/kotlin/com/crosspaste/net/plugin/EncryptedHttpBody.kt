package com.crosspaste.net.plugin

import com.crosspaste.net.ws.WS_MAX_FRAME_SIZE
import com.crosspaste.net.ws.WS_MAX_PAYLOAD_SIZE
import com.crosspaste.presist.FileTransferResourceLimits
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.io.IOException
import kotlinx.io.readByteArray

internal object EncryptedHttpResourceLimits {
    private const val AES_BLOCK_SIZE: Long = 16
    private const val MAX_CIPHER_OVERHEAD: Long = AES_BLOCK_SIZE * 2

    const val MAX_JSON_RESPONSE_PLAINTEXT_SIZE: Long = WS_MAX_PAYLOAD_SIZE
    const val MAX_JSON_RESPONSE_CIPHERTEXT_SIZE: Long =
        MAX_JSON_RESPONSE_PLAINTEXT_SIZE + MAX_CIPHER_OVERHEAD
    const val MAX_BINARY_RESPONSE_PLAINTEXT_SIZE: Long = FileTransferResourceLimits.MAX_CHUNK_SIZE
    const val MAX_BINARY_RECORD_CIPHERTEXT_SIZE: Long =
        FileTransferResourceLimits.MAX_CHUNK_SIZE + MAX_CIPHER_OVERHEAD

    fun requestCiphertextLimit(path: String): Long =
        plaintextToCiphertextLimit(
            when {
                // Register new routes with bodies larger than a control frame here.
                path == "/sync/paste" -> WS_MAX_PAYLOAD_SIZE
                path == "/sync/file/push" -> FileTransferResourceLimits.MAX_CHUNK_SIZE
                path.startsWith("/sync/icon/push/") -> FileTransferResourceLimits.MAX_ICON_SIZE
                else -> WS_MAX_FRAME_SIZE
            },
        )

    private fun plaintextToCiphertextLimit(plaintextLimit: Long): Long = plaintextLimit + MAX_CIPHER_OVERHEAD
}

internal class EncryptedPayloadException(
    message: String,
) : IOException(message)

internal fun Throwable.isEncryptedPayloadException(): Boolean =
    generateSequence(this) { current ->
        current.cause?.takeIf { it !== current }
    }.take(10).any { it is EncryptedPayloadException }

internal fun validateEncryptedContentLength(
    contentLength: Long?,
    maxBytes: Long,
    description: String,
) {
    if (contentLength != null && contentLength > maxBytes) {
        throw EncryptedPayloadException("$description exceeds $maxBytes bytes")
    }
}

internal suspend fun ByteReadChannel.readLimitedByteArray(
    maxBytes: Long,
    description: String,
): ByteArray {
    require(maxBytes in 0 until Int.MAX_VALUE) { "maxBytes must fit in a ByteArray" }
    val bytes = readRemaining(maxBytes + 1).readByteArray()
    if (bytes.size.toLong() > maxBytes) {
        throw EncryptedPayloadException("$description exceeds $maxBytes bytes")
    }
    return bytes
}

internal suspend fun decryptJsonResponse(
    channel: ByteReadChannel,
    maxCiphertextBytes: Long = EncryptedHttpResourceLimits.MAX_JSON_RESPONSE_CIPHERTEXT_SIZE,
    maxPlaintextBytes: Long = EncryptedHttpResourceLimits.MAX_JSON_RESPONSE_PLAINTEXT_SIZE,
    decrypt: (ByteArray) -> ByteArray,
): ByteArray {
    val encrypted = channel.readLimitedByteArray(maxCiphertextBytes, "encrypted JSON response")
    val decrypted = decrypt(encrypted)
    if (decrypted.size.toLong() > maxPlaintextBytes) {
        throw EncryptedPayloadException("decrypted JSON response exceeds $maxPlaintextBytes bytes")
    }
    return decrypted
}

internal suspend fun decryptChunkedResponse(
    channel: ByteReadChannel,
    maxRecordCiphertextBytes: Long = EncryptedHttpResourceLimits.MAX_BINARY_RECORD_CIPHERTEXT_SIZE,
    maxPlaintextBytes: Long = EncryptedHttpResourceLimits.MAX_BINARY_RESPONSE_PLAINTEXT_SIZE,
    decrypt: (ByteArray) -> ByteArray,
): ByteArray =
    buildPacket {
        var totalPlaintextBytes = 0L
        while (true) {
            val size = channel.readFrameSizeOrNull() ?: break
            if (size <= 0 || size.toLong() > maxRecordCiphertextBytes) {
                throw EncryptedPayloadException(
                    "encrypted binary record size $size is outside 1..$maxRecordCiphertextBytes",
                )
            }

            val encrypted = ByteArray(size)
            channel.readFully(encrypted)
            val decrypted = decrypt(encrypted)
            if (decrypted.size.toLong() > maxPlaintextBytes - totalPlaintextBytes) {
                throw EncryptedPayloadException(
                    "decrypted binary response exceeds $maxPlaintextBytes bytes",
                )
            }
            totalPlaintextBytes += decrypted.size
            writeFully(decrypted)
        }
    }.readByteArray()

private suspend fun ByteReadChannel.readFrameSizeOrNull(): Int? {
    if (!awaitContent(1)) return null
    if (!awaitContent(Int.SIZE_BYTES)) {
        throw EncryptedPayloadException("truncated encrypted binary record length")
    }
    return readInt()
}
