package com.crosspaste.utils

import java.io.ByteArrayOutputStream

actual fun getCodecsUtils(): CodecsUtils = DesktopCoreCodecsUtils

object DesktopCoreCodecsUtils : CodecsUtils {

    override fun hashByArray(array: Array<String>): String =
        if (array.isEmpty()) {
            ""
        } else if (array.size == 1) {
            hashByString(array[0])
        } else {
            val outputStream = ByteArrayOutputStream()
            array.forEach {
                outputStream.write(it.encodeToByteArray())
            }
            hash(outputStream.toByteArray())
        }
}
