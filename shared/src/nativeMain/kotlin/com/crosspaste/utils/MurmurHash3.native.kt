package com.crosspaste.utils

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.memcpy

actual class ByteOrder {
    companion object {
        val BIG_ENDIAN: ByteOrder = ByteOrder()
        val LITTLE_ENDIAN: ByteOrder = ByteOrder()

        @OptIn(ExperimentalForeignApi::class)
        val nativeOrder: ByteOrder by lazy {
            memScoped {
                val test = alloc<IntVar>()
                test.value = 0x01234567
                val bytes = test.ptr.reinterpret<ByteVar>()
                if (bytes[0].toInt() == 0x67) {
                    LITTLE_ENDIAN
                } else {
                    BIG_ENDIAN
                }
            }
        }
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
actual fun ByteArray.asULongArray(): ULongArray = asULongArrayLE()

@OptIn(ExperimentalUnsignedTypes::class, ExperimentalForeignApi::class)
fun ByteArray.asULongArrayLE(): ULongArray {
    require(size % 8 == 0) {
        "ByteArray size must be a multiple of 8, but was $size"
    }

    val ulongCount = size / 8
    val result = ULongArray(ulongCount)

    val isLittleEndian = ByteOrder.nativeOrder === ByteOrder.LITTLE_ENDIAN

    if (isLittleEndian) {
        this.usePinned { pinned ->
            memScoped {
                val src = pinned.addressOf(0)
                val dest = result.refTo(0).getPointer(this)
                memcpy(dest, src, size.convert())
            }
        }
    } else {
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
    }

    return result
}
