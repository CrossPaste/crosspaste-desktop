package com.crosspaste.utils

import okio.Path
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Base64

actual fun getCodecsUtils(): CodecsUtils {
    return DesktopCodecsUtils
}

object DesktopCodecsUtils : CodecsUtils {

    override fun base64Encode(bytes: ByteArray): String {
        return Base64.getEncoder().encodeToString(bytes)
    }

    override fun base64Decode(string: String): ByteArray {
        return Base64.getDecoder().decode(string)
    }

    override fun base64mimeEncode(bytes: ByteArray): String {
        return Base64.getMimeEncoder().encodeToString(bytes)
    }

    override fun base64mimeDecode(string: String): ByteArray {
        return Base64.getMimeDecoder().decode(string)
    }

    override fun hash(path: Path): String {
        val streamingMurmurHash3 = StreamingMurmurHash3(CROSS_PASTE_SEED)
        val bufferSize = 8192 * 10
        val buffer = ByteArray(bufferSize)

        path.toFile().inputStream().use { input ->
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                streamingMurmurHash3.update(buffer, 0, bytesRead)
            }
        }

        val (hash1, hash2) = streamingMurmurHash3.finish()
        return buildString(32) {
            appendHex(hash1)
            appendHex(hash2)
        }
    }

    override fun hashByArray(array: Array<String>): String {
        if (array.isEmpty()) {
            throw IllegalArgumentException("Array is empty")
        }
        if (array.size == 1) {
            return hashByString(array[0])
        } else {
            val outputStream = ByteArrayOutputStream()
            array.forEach {
                outputStream.write(it.toByteArray())
            }
            return hash(outputStream.toByteArray())
        }
    }

    override fun sha256(path: Path): String {
        val buffer = ByteArray(8192) // 8KB buffer
        val digest = MessageDigest.getInstance("SHA-256")
        var bytesRead: Int

        FileInputStream(path.toFile()).use { fis ->
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
