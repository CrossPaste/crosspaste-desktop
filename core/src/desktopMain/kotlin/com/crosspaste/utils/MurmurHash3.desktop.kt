package com.crosspaste.utils

import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(ExperimentalUnsignedTypes::class)
actual fun ByteArray.asULongArray(): ULongArray {
    require(size % 8 == 0) {
        "ByteArray size must be a multiple of 8, but was $size"
    }
    val buffer =
        ByteBuffer
            .wrap(this)
            .order(ByteOrder.LITTLE_ENDIAN)
    return ULongArray(size / 8) { buffer.getLong(it * 8).toULong() }
}
