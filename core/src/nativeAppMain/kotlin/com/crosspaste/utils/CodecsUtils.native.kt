package com.crosspaste.utils

actual fun getCodecsUtils(): CodecsUtils = NativeCoreCodecsUtils

object NativeCoreCodecsUtils : CodecsUtils {

    override fun hashByArray(array: Array<String>): String =
        if (array.isEmpty()) {
            ""
        } else if (array.size == 1) {
            hashByString(array[0])
        } else {
            val bytes = array.fold(ByteArray(0)) { acc, s -> acc + s.encodeToByteArray() }
            hash(bytes)
        }
}
