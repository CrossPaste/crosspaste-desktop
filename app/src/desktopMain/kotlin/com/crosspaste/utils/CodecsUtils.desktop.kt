package com.crosspaste.utils

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import java.io.ByteArrayOutputStream

actual fun getCodecsUtils(): CodecsUtils {
    return DesktopCodecsUtils
}

object DesktopCodecsUtils : CodecsUtils {

    override val fileUtils: FileUtils = getFileUtils()

    private val provider = CryptographyProvider.Default

    override val sha256 = provider.get(SHA256).hasher()

    override fun hashByArray(array: Array<String>): String {
        if (array.isEmpty()) {
            throw IllegalArgumentException("Array is empty")
        }
        return if (array.size == 1) {
            hashByString(array[0])
        } else {
            val outputStream = ByteArrayOutputStream()
            array.forEach {
                outputStream.write(it.toByteArray())
            }
            hash(outputStream.toByteArray())
        }
    }
}
