package com.crosspaste.info

import com.crosspaste.i18n.GlobalCopywriter

class PasteInfo(
    val key: String,
    val value: String,
    val converter: (String, copywriter: GlobalCopywriter) -> String,
) {

    fun getTextByCopyWriter(copywriter: GlobalCopywriter): String {
        return converter(value, copywriter)
    }

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
): PasteInfo {
    return PasteInfo(key, value) { it, _ -> it }
}

fun createPasteInfo(
    key: String,
    value: String,
): PasteInfo {
    return PasteInfo(key, value) { textKey, copywriter ->
        copywriter.getText(textKey)
    }
}

fun createPasteInfoCustomConverter(
    name: String,
    value: String,
    converter: (String, copywriter: GlobalCopywriter) -> String,
): PasteInfo {
    return PasteInfo(name, value, converter)
}
