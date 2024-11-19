package com.crosspaste.utils

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import java.io.ByteArrayOutputStream
import java.util.Base64

actual fun getCodecsUtils(): CodecsUtils {
    return DesktopCodecsUtils
}

object DesktopCodecsUtils : CodecsUtils {

    override val fileUtils: FileUtils = getFileUtils()

    private val provider = CryptographyProvider.Default

    override val sha256 = provider.get(SHA256).hasher()

    override fun base64Encode(bytes: ByteArray): String {
        return Base64.getEncoder().encodeToString(bytes)
    }

    override fun base64Decode(string: String): ByteArray {
        return Base64.getDecoder().decode(string)
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
}
