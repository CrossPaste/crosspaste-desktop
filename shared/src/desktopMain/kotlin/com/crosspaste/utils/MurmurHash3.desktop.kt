package com.crosspaste.utils

import java.nio.ByteBuffer

actual typealias ByteOrder = java.nio.ByteOrder

@OptIn(ExperimentalUnsignedTypes::class)
actual fun ByteArray.asULongArray(): ULongArray {
    val buffer =
        ByteBuffer
            .wrap(this)
            .order(ByteOrder.LITTLE_ENDIAN)
    return ULongArray(size / 8) { buffer.getLong(it * 8).toULong() }
}
