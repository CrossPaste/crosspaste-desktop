package com.crosspaste.info

import com.crosspaste.i18n.GlobalCopywriter

class PasteInfo(
    val key: String,
    val value: String,
    val converter: (String, copywriter: GlobalCopywriter) -> String,
) {

    fun getTextByCopyWriter(copywriter: GlobalCopywriter): String = converter(value, copywriter)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PasteInfo) return false

        if (key != other.key) return false
        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }
}

fun createPasteInfoWithoutConverter(
    key: String,
    value: String,
): PasteInfo = PasteInfo(key, value) { it, _ -> it }
