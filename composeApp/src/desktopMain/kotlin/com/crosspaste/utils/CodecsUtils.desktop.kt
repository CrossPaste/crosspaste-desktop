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

    override fun md5(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    override fun md5ByArray(array: Array<String>): String {
        if (array.isEmpty()) {
            throw IllegalArgumentException("Array is empty")
        }
        if (array.size == 1) {
            return array[0]
        } else {
            val outputStream = ByteArrayOutputStream()
            array.forEach {
                outputStream.write(it.toByteArray())
            }
            return md5(outputStream.toByteArray())
        }
    }

    override fun md5ByString(string: String): String {
        return md5(string.toByteArray())
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
