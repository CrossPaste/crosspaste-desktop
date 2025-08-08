package com.crosspaste.utils

import com.fleeksoft.io.ByteArrayOutputStream
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256

actual fun getCodecsUtils(): CodecsUtils = NativeCodecsUtils

object NativeCodecsUtils : CodecsUtils {

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
                outputStream.write(it.encodeToByteArray())
            }
            hash(outputStream.toByteArray())
        }
    }
}
