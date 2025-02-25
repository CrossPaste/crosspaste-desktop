package com.crosspaste.utils

actual typealias ByteOrder = java.nio.ByteOrder

@OptIn(ExperimentalUnsignedTypes::class)
actual fun ByteArray.asULongArray(): ULongArray {
    val buffer = java.nio.ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    return ULongArray(size / 8) { buffer.getLong(it * 8).toULong() }
}
