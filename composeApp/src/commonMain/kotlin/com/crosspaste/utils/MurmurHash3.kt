package com.crosspaste.utils

import kotlin.math.min

expect class ByteOrder

@OptIn(ExperimentalUnsignedTypes::class)
expect fun ByteArray.asULongArray(): ULongArray

class MurmurHash3(private val seed: UInt = 0u) {
    fun hash32x86(key: ByteArray): UInt {
        var h = seed
        val len = key.size
        val nblocks = len / 4

        for (i in 0 until nblocks * 4 step 4) {
            val k = key.getLittleEndianUInt(i)

            h = h xor k.mix(R1_32, C1_32, C2_32)
            h = h.rotateLeft(R2_32)
            h = h * M_32 + N_32
        }

        val index = nblocks * 4
        val rem = len - index
        var k = 0u
        if (rem == 3) {
            k = k xor (key.getUInt(index + 2) shl 16)
        }
        if (rem >= 2) {
            k = k xor (key.getUInt(index + 1) shl 8)
        }
        if (rem >= 1) {
            k = k xor key.getUInt(index)
            h = h xor k.mix(R1_32, C1_32, C2_32)
        }

        h = h xor len.toUInt()

        h = h.fmix()

        return h
    }

    public fun hash128x86(key: ByteArray): Array<UInt> {
        var h1 = seed
        var h2 = seed
        var h3 = seed
        var h4 = seed
        val len = key.size
        val nblocks = len / 16

        for (i in 0 until nblocks * 16 step 16) {
            val k1 = key.getLittleEndianUInt(i)
            val k2 = key.getLittleEndianUInt(i + 4)
            val k3 = key.getLittleEndianUInt(i + 8)
            val k4 = key.getLittleEndianUInt(i + 12)

            h1 = h1 xor k1.mix(R1_128x86, C1_128x86, C2_128x86)
            h1 = h1.rotateLeft(R5_128x86)
            h1 += h2
            h1 = h1 * M_128x86 + N1_128x86

            h2 = h2 xor k2.mix(R2_128x86, C2_128x86, C3_128x86)
            h2 = h2.rotateLeft(R3_128x86)
            h2 += h3
            h2 = h2 * M_128x86 + N2_128x86

            h3 = h3 xor k3.mix(R3_128x86, C3_128x86, C4_128x86)
            h3 = h3.rotateLeft(R1_128x86)
            h3 += h4
            h3 = h3 * M_128x86 + N3_128x86

            h4 = h4 xor k4.mix(R4_128x86, C4_128x86, C1_128x86)
            h4 = h4.rotateLeft(R6_128x86)
            h4 += h1
            h4 = h4 * M_128x86 + N4_128x86
        }

        val index = nblocks * 16
        val rem = len - index
        var k1 = 0u
        var k2 = 0u
        var k3 = 0u
        var k4 = 0u
        if (rem == 15) {
            k4 = k4 xor (key.getUInt(index + 14) shl 16)
        }
        if (rem >= 14) {
            k4 = k4 xor (key.getUInt(index + 13) shl 8)
        }
        if (rem >= 13) {
            k4 = k4 xor key.getUInt(index + 12)
            h4 = h4 xor k4.mix(R4_128x86, C4_128x86, C1_128x86)
        }
        if (rem >= 12) {
            k3 = k3 xor (key.getUInt(index + 11) shl 24)
        }
        if (rem >= 11) {
            k3 = k3 xor (key.getUInt(index + 10) shl 16)
        }
        if (rem >= 10) {
            k3 = k3 xor (key.getUInt(index + 9) shl 8)
        }
        if (rem >= 9) {
            k3 = k3 xor key.getUInt(index + 8)
            h3 = h3 xor k3.mix(R3_128x86, C3_128x86, C4_128x86)
        }
        if (rem >= 8) {
            k2 = k2 xor (key.getUInt(index + 7) shl 24)
        }
        if (rem >= 7) {
            k2 = k2 xor (key.getUInt(index + 6) shl 16)
        }
        if (rem >= 6) {
            k2 = k2 xor (key.getUInt(index + 5) shl 8)
        }
        if (rem >= 5) {
            k2 = k2 xor key.getUInt(index + 4)
            h2 = h2 xor k2.mix(R2_128x86, C2_128x86, C3_128x86)
        }
        if (rem >= 4) {
            k1 = k1 xor (key.getUInt(index + 3) shl 24)
        }
        if (rem >= 3) {
            k1 = k1 xor (key.getUInt(index + 2) shl 16)
        }
        if (rem >= 2) {
            k1 = k1 xor (key.getUInt(index + 1) shl 8)
        }
        if (rem >= 1) {
            k1 = k1 xor key.getUInt(index)
            h1 = h1 xor k1.mix(R1_128x86, C1_128x86, C2_128x86)
        }

        h1 = h1 xor len.toUInt()
        h2 = h2 xor len.toUInt()
        h3 = h3 xor len.toUInt()
        h4 = h4 xor len.toUInt()

        h1 += h2
        h1 += h3
        h1 += h4
        h2 += h1
        h3 += h1
        h4 += h1

        h1 = h1.fmix()
        h2 = h2.fmix()
        h3 = h3.fmix()
        h4 = h4.fmix()

        h1 += h2
        h1 += h3
        h1 += h4
        h2 += h1
        h3 += h1
        h4 += h1

        return arrayOf(h1, h2, h3, h4)
    }

    fun hash128x64(key: ByteArray): Array<ULong> {
        var h1 = seed.toULong()
        var h2 = seed.toULong()
        val len = key.size
        val nblocks = len / 16

        for (i in 0 until nblocks * 16 step 16) {
            val k1 = key.getLittleEndianLong(i)
            val k2 = key.getLittleEndianLong(i + 8)

            h1 = h1 xor k1.mix(R1_128x64, C1_128x64, C2_128x64)
            h1 = h1.rotateLeft(R2_128x64)
            h1 += h2
            h1 = h1 * M_128x64 + N1_128x64

            h2 = h2 xor k2.mix(R3_128x64, C2_128x64, C1_128x64)
            h2 = h2.rotateLeft(R1_128x64)
            h2 += h1
            h2 = h2 * M_128x64 + N2_128x64
        }

        val index = nblocks * 16
        val rem = len - index
        var k1 = 0uL
        var k2 = 0uL
        if (rem == 15) {
            k2 = k2 xor (key.getULong(index + 14) shl 48)
        }
        if (rem >= 14) {
            k2 = k2 xor (key.getULong(index + 13) shl 40)
        }
        if (rem >= 13) {
            k2 = k2 xor (key.getULong(index + 12) shl 32)
        }
        if (rem >= 12) {
            k2 = k2 xor (key.getULong(index + 11) shl 24)
        }
        if (rem >= 11) {
            k2 = k2 xor (key.getULong(index + 10) shl 16)
        }
        if (rem >= 10) {
            k2 = k2 xor (key.getULong(index + 9) shl 8)
        }
        if (rem >= 9) {
            k2 = k2 xor key.getULong(index + 8)
            h2 = h2 xor k2.mix(R3_128x64, C2_128x64, C1_128x64)
        }
        if (rem >= 8) {
            k1 = k1 xor (key.getULong(index + 7) shl 56)
        }
        if (rem >= 7) {
            k1 = k1 xor (key.getULong(index + 6) shl 48)
        }
        if (rem >= 6) {
            k1 = k1 xor (key.getULong(index + 5) shl 40)
        }
        if (rem >= 5) {
            k1 = k1 xor (key.getULong(index + 4) shl 32)
        }
        if (rem >= 4) {
            k1 = k1 xor (key.getULong(index + 3) shl 24)
        }
        if (rem >= 3) {
            k1 = k1 xor (key.getULong(index + 2) shl 16)
        }
        if (rem >= 2) {
            k1 = k1 xor (key.getULong(index + 1) shl 8)
        }
        if (rem >= 1) {
            k1 = k1 xor key.getULong(index)
            h1 = h1 xor k1.mix(R1_128x64, C1_128x64, C2_128x64)
        }

        h1 = h1 xor len.toULong()
        h2 = h2 xor len.toULong()

        h1 += h2
        h2 += h1

        h1 = h1.fmix()
        h2 = h2.fmix()

        h1 += h2
        h2 += h1

        return arrayOf(h1, h2)
    }

    private fun ByteArray.getLittleEndianUInt(index: Int): UInt {
        return this.getUInt(index) or
            (this.getUInt(index + 1) shl 8) or
            (this.getUInt(index + 2) shl 16) or
            (this.getUInt(index + 3) shl 24)
    }

    private fun ByteArray.getLittleEndianLong(index: Int): ULong {
        return this.getULong(index) or
            (this.getULong(index + 1) shl 8) or
            (this.getULong(index + 2) shl 16) or
            (this.getULong(index + 3) shl 24) or
            (this.getULong(index + 4) shl 32) or
            (this.getULong(index + 5) shl 40) or
            (this.getULong(index + 6) shl 48) or
            (this.getULong(index + 7) shl 56)
    }

    private fun UInt.mix(
        r: Int,
        c1: UInt,
        c2: UInt,
    ): UInt {
        var k = this
        k *= c1
        k = k.rotateLeft(r)
        k *= c2
        return k
    }

    private fun ULong.mix(
        r: Int,
        c1: ULong,
        c2: ULong,
    ): ULong {
        var k = this
        k *= c1
        k = k.rotateLeft(r)
        k *= c2
        return k
    }

    private fun UInt.fmix(): UInt {
        var h = this
        h = h xor (h shr 16)
        h *= 0x85ebca6bu
        h = h xor (h shr 13)
        h *= 0xc2b2ae35u
        h = h xor (h shr 16)
        return h
    }

    private fun ULong.fmix(): ULong {
        var h = this
        h = h xor (h shr 33)
        h *= 0xff51afd7ed558ccduL
        h = h xor (h shr 33)
        h *= 0xc4ceb9fe1a85ec53uL
        h = h xor (h shr 33)
        return h
    }

    private fun ByteArray.getUInt(index: Int) = get(index).toUByte().toUInt()

    private fun ByteArray.getULong(index: Int) = get(index).toUByte().toULong()

    private companion object {
        private const val C1_32: UInt = 0xcc9e2d51u
        private const val C2_32: UInt = 0x1b873593u

        private const val R1_32: Int = 15
        private const val R2_32: Int = 13

        private const val M_32: UInt = 5u
        private const val N_32: UInt = 0xe6546b64u

        private const val C1_128x86: UInt = 0x239b961bu
        private const val C2_128x86: UInt = 0xab0e9789u
        private const val C3_128x86: UInt = 0x38b34ae5u
        private const val C4_128x86: UInt = 0xa1e38b93u

        private const val R1_128x86: Int = 15
        private const val R2_128x86: Int = 16
        private const val R3_128x86: Int = 17
        private const val R4_128x86: Int = 18
        private const val R5_128x86: Int = 19
        private const val R6_128x86: Int = 13

        private const val M_128x86: UInt = 5u
        private const val N1_128x86: UInt = 0x561ccd1bu
        private const val N2_128x86: UInt = 0x0bcaa747u
        private const val N3_128x86: UInt = 0x96cd1c35u
        private const val N4_128x86: UInt = 0x32ac3b17u

        private const val C1_128x64: ULong = 0x87c37b91114253d5uL
        private const val C2_128x64: ULong = 0x4cf5ad432745937fuL

        private const val R1_128x64: Int = 31
        private const val R2_128x64: Int = 27
        private const val R3_128x64: Int = 33

        private const val M_128x64: ULong = 5u
        private const val N1_128x64: ULong = 0x52dce729u
        private const val N2_128x64: ULong = 0x38495ab5u
    }
}

class StreamingMurmurHash3(seed: UInt = 0u) {
    private var h1 = seed.toULong()
    private var h2 = seed.toULong()
    private var totalLen = 0UL
    private val buffer = ByteArray(16)
    private var bufferSize = 0

    @OptIn(ExperimentalUnsignedTypes::class)
    fun update(
        input: ByteArray,
        offset: Int = 0,
        length: Int = input.size,
    ) {
        var currentOffset = offset
        var remainingLength = length

        totalLen += length.toULong()

        // First, process any data left in the buffer
        if (bufferSize > 0) {
            val bytesToCopy = min(16 - bufferSize, remainingLength)
            input.copyInto(buffer, bufferSize, currentOffset, currentOffset + bytesToCopy)
            bufferSize += bytesToCopy
            currentOffset += bytesToCopy
            remainingLength -= bytesToCopy

            if (bufferSize == 16) {
                processBuffer()
            }
        }

        // Process 16-byte chunks
        while (remainingLength >= 16) {
            val chunk = input.sliceArray(currentOffset until currentOffset + 16)
            val (k1, k2) = chunk.asULongArray()
            processChunk(k1, k2)
            currentOffset += 16
            remainingLength -= 16
        }

        // Store remaining bytes in the buffer
        if (remainingLength > 0) {
            input.copyInto(buffer, 0, currentOffset, currentOffset + remainingLength)
            bufferSize = remainingLength
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun finish(): Array<ULong> {
        // Process any remaining data in the buffer
        if (bufferSize > 0) {
            var k1 = 0uL
            var k2 = 0uL

            when (bufferSize) {
                15 -> k2 = k2 xor (buffer[14].toULong() shl 48)
                14 -> k2 = k2 xor (buffer[13].toULong() shl 40)
                13 -> k2 = k2 xor (buffer[12].toULong() shl 32)
                12 -> k2 = k2 xor (buffer[11].toULong() shl 24)
                11 -> k2 = k2 xor (buffer[10].toULong() shl 16)
                10 -> k2 = k2 xor (buffer[9].toULong() shl 8)
                9 -> {
                    k2 = k2 xor buffer[8].toULong()
                    k2 = k2.mix(R3_128x64, C2_128x64, C1_128x64)
                    h2 = h2 xor k2
                }
            }

            if (bufferSize > 8) {
                k1 = buffer.sliceArray(0 until 8).asULongArray()[0]
            } else if (bufferSize > 0) {
                for (i in 0 until bufferSize) {
                    k1 = k1 xor (buffer[i].toULong() shl (i * 8))
                }
            }

            h1 = h1 xor k1.mix(R1_128x64, C1_128x64, C2_128x64)
        }

        h1 = h1 xor totalLen
        h2 = h2 xor totalLen

        h1 += h2
        h2 += h1

        h1 = h1.fmix()
        h2 = h2.fmix()

        h1 += h2
        h2 += h1

        return arrayOf(h1, h2)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun processBuffer() {
        val (k1, k2) = buffer.asULongArray()
        processChunk(k1, k2)
        bufferSize = 0
    }

    private fun processChunk(
        k1: ULong,
        k2: ULong,
    ) {
        var h1 = this.h1
        var h2 = this.h2

        h1 = h1 xor k1.mix(R1_128x64, C1_128x64, C2_128x64)
        h1 = h1.rotateLeft(R2_128x64)
        h1 += h2
        h1 = h1 * M_128x64 + N1_128x64

        h2 = h2 xor k2.mix(R3_128x64, C2_128x64, C1_128x64)
        h2 = h2.rotateLeft(R1_128x64)
        h2 += h1
        h2 = h2 * M_128x64 + N2_128x64

        this.h1 = h1
        this.h2 = h2
    }

    private fun ULong.mix(
        r: Int,
        c1: ULong,
        c2: ULong,
    ): ULong {
        var k = this
        k *= c1
        k = k.rotateLeft(r)
        k *= c2
        return k
    }

    private fun ULong.fmix(): ULong {
        var h = this
        h = h xor (h shr 33)
        h *= 0xff51afd7ed558ccduL
        h = h xor (h shr 33)
        h *= 0xc4ceb9fe1a85ec53uL
        h = h xor (h shr 33)
        return h
    }

    companion object {
        private const val C1_128x64: ULong = 0x87c37b91114253d5uL
        private const val C2_128x64: ULong = 0x4cf5ad432745937fuL

        private const val R1_128x64: Int = 31
        private const val R2_128x64: Int = 27
        private const val R3_128x64: Int = 33

        private const val M_128x64: ULong = 5uL
        private const val N1_128x64: ULong = 0x52dce729uL
        private const val N2_128x64: ULong = 0x38495ab5uL
    }
}
