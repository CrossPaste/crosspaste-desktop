package com.crosspaste.utils

actual fun getCodecsUtils(): CodecsUtils = JsCodecsUtils

object JsCodecsUtils : CodecsUtils {

    override fun hashByArray(array: Array<String>): String =
        if (array.isEmpty()) {
            ""
        } else if (array.size == 1) {
            hashByString(array[0])
        } else {
            val combined = array.joinToString("").encodeToByteArray()
            hash(combined)
        }
}
