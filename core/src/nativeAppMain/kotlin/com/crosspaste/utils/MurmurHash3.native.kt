package com.crosspaste.utils

@OptIn(ExperimentalUnsignedTypes::class)
actual fun ByteArray.asULongArray(): ULongArray {
    require(size % 8 == 0) {
        "ByteArray size must be a multiple of 8, but was $size"
    }

    val ulongCount = size / 8
    val result = ULongArray(ulongCount)

    for (i in 0 until ulongCount) {
        val offset = i * 8
        result[i] = this[offset].toUByte().toULong() or
            (this[offset + 1].toUByte().toULong() shl 8) or
            (this[offset + 2].toUByte().toULong() shl 16) or
            (this[offset + 3].toUByte().toULong() shl 24) or
            (this[offset + 4].toUByte().toULong() shl 32) or
            (this[offset + 5].toUByte().toULong() shl 40) or
            (this[offset + 6].toUByte().toULong() shl 48) or
            (this[offset + 7].toUByte().toULong() shl 56)
    }

    return result
}
